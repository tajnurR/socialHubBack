package com.socialhub.socialhubBackend.integration.linkedin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialhub.socialhubBackend.common.exception.BusinessException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

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

    public CreatePostResponse createTextPost(
            String authorUrn, String accessToken, String commentary, String apiVersion) {
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
        return call(
                () -> {
                    var response = client.post()
                            .uri("/rest/posts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .header("Linkedin-Version", resolveApiVersion(apiVersion))
                            .header("X-Restli-Protocol-Version", "2.0.0")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    String postId = response.getHeaders().getFirst("x-restli-id");
                    return new CreatePostResponse(postId == null || postId.isBlank() ? "LINKEDIN_POST" : postId);
                },
                "create LinkedIn post");
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
                    "LinkedIn permission was denied. Reconnect and approve openid, profile, email, and w_member_social.",
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

    public record CreatePostResponse(String id) {}

    private record LinkedInError(Integer serviceErrorCode, String status, String message) {}
}
