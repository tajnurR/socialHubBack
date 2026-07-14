package com.socialhub.socialhubBackend.integration.linkedin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialhub.socialhubBackend.common.exception.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LinkedInClient {

    private static final Logger log = LoggerFactory.getLogger(LinkedInClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);
    private static final Pattern ORGANIZATION_ID = Pattern.compile("urn:li:organization(?:Brand)?:(\\d+)");

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkedInProperties properties;

    public LinkedInClient(LinkedInProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.client = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public TokenResponse exchangeAuthorizationCode(
            String code, String redirectUri, String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return call(
                () -> RestClient.builder()
                        .requestFactory(clientRequestFactory())
                        .build()
                        .post()
                        .uri(properties.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class),
                "exchange LinkedIn authorization code");
    }

    public UserInfoResponse getUserInfo(String accessToken) {
        return call(
                () -> client.get()
                        .uri("/v2/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(UserInfoResponse.class),
                "load LinkedIn profile");
    }

    public List<OrganizationOption> getAdminOrganizations(String accessToken, String apiVersion) {
        OrganizationAclsResponse acls = call(
                () -> client.get()
                        .uri(uri -> uri.path("/rest/organizationAcls")
                                .queryParam("q", "roleAssignee")
                                .queryParam("role", "ADMINISTRATOR")
                                .queryParam("state", "APPROVED")
                                .queryParam("count", 100)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Linkedin-Version", resolveApiVersion(apiVersion))
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .retrieve()
                        .body(OrganizationAclsResponse.class),
                "list LinkedIn organization admin access");
        List<String> organizationUrns = acls == null || acls.elements() == null
                ? List.of()
                : acls.elements().stream()
                        .map(OrganizationAclElement::organizationUrn)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (organizationUrns.isEmpty()) {
            return List.of();
        }
        Map<String, OrganizationResponse> organizations =
                getOrganizationsById(accessToken, apiVersion, organizationUrns);
        return organizationUrns.stream()
                .map(urn -> {
                    String id = organizationId(urn);
                    OrganizationResponse organization = organizations.get(id);
                    String name = organization == null || organization.localizedName() == null
                            || organization.localizedName().isBlank()
                            ? "LinkedIn Page " + id
                            : organization.localizedName();
                    return new OrganizationOption("urn:li:organization:" + id, name);
                })
                .toList();
    }

    private Map<String, OrganizationResponse> getOrganizationsById(
            String accessToken, String apiVersion, List<String> organizationUrns) {
        List<String> ids = organizationUrns.stream()
                .map(this::organizationId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String path = "/rest/organizations?ids=List(" + String.join(",", ids) + ")";
        OrganizationsResponse response = call(
                () -> client.get()
                        .uri(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Linkedin-Version", resolveApiVersion(apiVersion))
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .retrieve()
                        .body(OrganizationsResponse.class),
                "load LinkedIn organizations");
        return response == null || response.results() == null ? Map.of() : response.results();
    }

    private String organizationId(String urn) {
        if (urn == null || urn.isBlank()) {
            return null;
        }
        Matcher matcher = ORGANIZATION_ID.matcher(urn);
        return matcher.matches() ? matcher.group(1) : urn;
    }

    public CreatePostResponse createTextPost(
            String authorUrn, String accessToken, String commentary, String apiVersion) {
        return createPost(authorUrn, accessToken, commentary, null, null, apiVersion);
    }

    public CreatePostResponse createPost(
            String authorUrn,
            String accessToken,
            String commentary,
            String mediaUrn,
            String mediaTitle,
            String apiVersion) {
        Map<String, Object> body = Map.of(
                "author", authorUrn,
                "commentary", commentary,
                "visibility", "PUBLIC",
                "distribution", Map.of(
                        "feedDistribution", "MAIN_FEED",
                        "targetEntities", java.util.List.of(),
                        "thirdPartyDistributionChannels", java.util.List.of()),
                "lifecycleState", "PUBLISHED",
                "isReshareDisabledByAuthor", false);
        if (mediaUrn != null && !mediaUrn.isBlank()) {
            body = new LinkedHashMap<>(body);
            body.put("content", Map.of(
                    "media", Map.of(
                            "id", mediaUrn,
                            "title", mediaTitle == null || mediaTitle.isBlank() ? "SocialHub media" : mediaTitle)));
        }
        Map<String, Object> requestBody = body;
        return call(
                () -> {
                    var response = client.post()
                            .uri("/rest/posts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .header("Linkedin-Version", resolveApiVersion(apiVersion))
                            .header("X-Restli-Protocol-Version", "2.0.0")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .toBodilessEntity();
                    String postId = response.getHeaders().getFirst("x-restli-id");
                    return new CreatePostResponse(postId == null || postId.isBlank() ? "LINKEDIN_POST" : postId);
                },
                "create LinkedIn post");
    }

    public String uploadImage(
            String ownerUrn,
            String accessToken,
            String filename,
            String contentType,
            byte[] body,
            String apiVersion) {
        if (body == null || body.length == 0) {
            throw new BusinessException("LinkedIn image upload requires a media file.");
        }
        InitializeImageUploadResponse initialized = initializeImageUpload(ownerUrn, accessToken, apiVersion);
        if (initialized == null || initialized.value() == null || initialized.value().uploadUrl() == null) {
            throw new BusinessException("LinkedIn did not return an image upload URL.");
        }
        uploadBinary(initialized.value().uploadUrl(), accessToken, contentType, body, "upload LinkedIn image");
        return initialized.value().image();
    }

    public String uploadVideo(
            String ownerUrn,
            String accessToken,
            String filename,
            String contentType,
            byte[] body,
            String apiVersion) {
        if (body == null || body.length == 0) {
            throw new BusinessException("LinkedIn video upload requires a media file.");
        }
        if (!looksLikeMp4(filename, contentType)) {
            throw new BusinessException("LinkedIn video posts require an MP4 video file.");
        }
        InitializeVideoUploadResponse initialized =
                initializeVideoUpload(ownerUrn, accessToken, body.length, apiVersion);
        if (initialized == null || initialized.value() == null || initialized.value().uploadInstructions() == null) {
            throw new BusinessException("LinkedIn did not return video upload instructions.");
        }
        List<String> uploadedPartIds = new ArrayList<>();
        for (VideoUploadInstruction instruction : initialized.value().uploadInstructions()) {
            long firstByte = instruction.firstByte() == null ? 0 : instruction.firstByte();
            long lastByte = instruction.lastByte() == null ? body.length - 1L : instruction.lastByte();
            byte[] part = slice(body, firstByte, lastByte);
            ResponseEntity<Void> response = uploadBinary(
                    instruction.uploadUrl(),
                    accessToken,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    part,
                    "upload LinkedIn video part");
            String etag = response.getHeaders().getETag();
            if (etag == null || etag.isBlank()) {
                etag = response.getHeaders().getFirst("ETag");
            }
            if (etag != null && !etag.isBlank()) {
                uploadedPartIds.add(stripQuotes(etag));
            }
        }
        finalizeVideoUpload(
                initialized.value().video(),
                initialized.value().uploadToken(),
                uploadedPartIds,
                accessToken,
                apiVersion);
        return initialized.value().video();
    }

    private InitializeImageUploadResponse initializeImageUpload(
            String ownerUrn, String accessToken, String apiVersion) {
        return call(
                () -> client.post()
                        .uri("/rest/images?action=initializeUpload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Linkedin-Version", resolveApiVersion(apiVersion))
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("initializeUploadRequest", Map.of("owner", ownerUrn)))
                        .retrieve()
                        .body(InitializeImageUploadResponse.class),
                "initialize LinkedIn image upload");
    }

    private InitializeVideoUploadResponse initializeVideoUpload(
            String ownerUrn, String accessToken, int fileSizeBytes, String apiVersion) {
        return call(
                () -> client.post()
                        .uri("/rest/videos?action=initializeUpload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Linkedin-Version", resolveApiVersion(apiVersion))
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "initializeUploadRequest",
                                Map.of(
                                        "owner", ownerUrn,
                                        "fileSizeBytes", fileSizeBytes,
                                        "uploadCaptions", false,
                                        "uploadThumbnail", false)))
                        .retrieve()
                        .body(InitializeVideoUploadResponse.class),
                "initialize LinkedIn video upload");
    }

    private void finalizeVideoUpload(
            String videoUrn,
            String uploadToken,
            List<String> uploadedPartIds,
            String accessToken,
            String apiVersion) {
        call(
                () -> client.post()
                        .uri("/rest/videos?action=finalizeUpload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .header("Linkedin-Version", resolveApiVersion(apiVersion))
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "finalizeUploadRequest",
                                Map.of(
                                        "video", videoUrn,
                                        "uploadToken", uploadToken == null ? "" : uploadToken,
                                        "uploadedPartIds", uploadedPartIds)))
                        .retrieve()
                        .toBodilessEntity(),
                "finalize LinkedIn video upload");
    }

    private ResponseEntity<Void> uploadBinary(
            String uploadUrl, String accessToken, String contentType, byte[] body, String action) {
        return call(
                () -> RestClient.builder()
                        .requestFactory(clientRequestFactory())
                        .build()
                        .put()
                        .uri(uploadUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(mediaType(contentType))
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                action);
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private byte[] slice(byte[] body, long firstByte, long lastByte) {
        int from = Math.max(0, (int) firstByte);
        int to = Math.min(body.length, (int) lastByte + 1);
        if (from >= to) {
            return new byte[0];
        }
        return Arrays.copyOfRange(body, from, to);
    }

    private boolean looksLikeMp4(String filename, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        String name = filename == null ? "" : filename.toLowerCase();
        return type.contains("mp4") || name.endsWith(".mp4");
    }

    private String stripQuotes(String value) {
        String trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }

    private SimpleClientHttpRequestFactory clientRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return requestFactory;
    }

    private String resolveApiVersion(String apiVersion) {
        return apiVersion != null && !apiVersion.isBlank() ? apiVersion : properties.apiVersion();
    }

    private <T> T call(Supplier<T> request, String action) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            LinkedInError error = parseError(ex.getResponseBodyAsString());
            log.warn(
                    "LinkedIn API error during [{}]: httpStatus={} serviceErrorCode={} message={}",
                    action,
                    ex.getStatusCode().value(),
                    error.serviceErrorCode(),
                    error.message());
            throw mapLinkedInError(error, ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
            log.warn("Network failure reaching LinkedIn during [{}]: {}", action, root.getMessage());
            throw new BusinessException("Could not reach LinkedIn. Try again later.", HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.warn("Failed to process LinkedIn response during [{}]: {}", action, ex.getMessage());
            throw new BusinessException("LinkedIn returned an unreadable response.", HttpStatus.BAD_GATEWAY);
        }
    }

    private RuntimeException mapLinkedInError(LinkedInError error, int httpStatus) {
        String message = error.message() == null ? "" : error.message();
        String lower = message.toLowerCase();
        if (httpStatus == 401 || lower.contains("invalid access token") || lower.contains("expired")) {
            return new BusinessException(
                    "LinkedIn authorization expired or was rejected. Reconnect LinkedIn.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (httpStatus == 403 || lower.contains("scope") || lower.contains("permission")) {
            return new BusinessException(
                    "LinkedIn permission was denied. Reconnect and approve openid, profile, email, w_member_social, r_organization_admin, and w_organization_social.",
                    HttpStatus.FORBIDDEN);
        }
        if (lower.contains("redirect uri") || lower.contains("invalid_redirect_uri")) {
            return new BusinessException(
                    "LinkedIn rejected the redirect URI. Add the exact SocialHub LinkedIn callback URL to your LinkedIn app.",
                    HttpStatus.BAD_REQUEST);
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return new BusinessException(
                    message.isBlank() ? "LinkedIn rejected the request. Check your app setup." : message,
                    HttpStatus.BAD_REQUEST);
        }
        return new BusinessException("LinkedIn is unavailable. Try again later.", HttpStatus.BAD_GATEWAY);
    }

    private LinkedInError parseError(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedInError(null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = text(node, "message");
            if (message == null) {
                message = text(node, "error_description");
            }
            if (message == null) {
                message = text(node, "error");
            }
            Integer serviceErrorCode = node.has("serviceErrorCode") ? node.get("serviceErrorCode").asInt() : null;
            return new LinkedInError(serviceErrorCode, text(node, "status"), message);
        } catch (Exception ignored) {
            return new LinkedInError(null, null, body);
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
            String scope,
            @JsonProperty("id_token") String idToken) {}

    public record UserInfoResponse(
            String sub,
            String name,
            String email,
            String picture,
            @JsonProperty("given_name") String givenName,
            @JsonProperty("family_name") String familyName) {}

    public record OrganizationAclsResponse(List<OrganizationAclElement> elements) {}

    public record OrganizationAclElement(
            String organization,
            String organizationTarget,
            String role,
            String state) {
        String organizationUrn() {
            return organization != null && !organization.isBlank() ? organization : organizationTarget;
        }
    }

    public record OrganizationsResponse(Map<String, OrganizationResponse> results) {}

    public record OrganizationResponse(
            Long id,
            String localizedName,
            String vanityName) {}

    public record OrganizationOption(String id, String name) {}

    public record CreatePostResponse(String id) {}

    public record InitializeImageUploadResponse(ImageUploadValue value) {}

    public record ImageUploadValue(
            @JsonProperty("uploadUrlExpiresAt") Long uploadUrlExpiresAt,
            String uploadUrl,
            String image) {}

    public record InitializeVideoUploadResponse(VideoUploadValue value) {}

    public record VideoUploadValue(
            @JsonProperty("uploadUrlsExpireAt") Long uploadUrlsExpireAt,
            String video,
            List<VideoUploadInstruction> uploadInstructions,
            String uploadToken) {}

    public record VideoUploadInstruction(String uploadUrl, Long firstByte, Long lastByte) {}

    private record LinkedInError(Integer serviceErrorCode, String status, String message) {}
}
