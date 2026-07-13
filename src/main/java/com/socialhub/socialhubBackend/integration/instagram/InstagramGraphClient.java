package com.socialhub.socialhubBackend.integration.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class InstagramGraphClient {

    private static final Logger log = LoggerFactory.getLogger(InstagramGraphClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InstagramProperties properties;

    public InstagramGraphClient(InstagramProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public TokenResponse exchangeAuthorizationCode(
            String code, String redirectUri, String appId, String appSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", appId);
        form.add("client_secret", appSecret);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        return call(
                () -> client.post()
                        .uri(properties.tokenUrl())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class),
                "exchange Instagram authorization code");
    }

    public TokenResponse exchangeForLongLivedToken(String shortLivedAccessToken, String appSecret) {
        URI uri = UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                .path("/access_token")
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", appSecret)
                .queryParam("access_token", shortLivedAccessToken)
                .build()
                .toUri();
        return call(
                () -> client.get().uri(uri).retrieve().body(TokenResponse.class),
                "exchange Instagram long-lived token");
    }

    public ProfileResponse getProfile(String accessToken, String apiVersion) {
        URI uri = versionedUri(apiVersion)
                .pathSegment("me")
                .queryParam("fields", "id,username,name,account_type,profile_picture_url")
                .build()
                .toUri();
        return call(
                () -> client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(ProfileResponse.class),
                "load Instagram profile");
    }

    public CreateResponse createMediaContainer(
            String instagramAccountId,
            String accessToken,
            String mediaUrl,
            PostMediaType mediaType,
            String caption,
            String apiVersion) {
        URI uri = versionedUri(apiVersion).pathSegment(instagramAccountId, "media").build().toUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (mediaType == PostMediaType.VIDEO) {
            form.add("media_type", "REELS");
            form.add("video_url", mediaUrl);
        } else {
            form.add("image_url", mediaUrl);
        }
        if (caption != null && !caption.isBlank()) {
            form.add("caption", caption);
        }
        return call(
                () -> client.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Instagram media container");
    }

    public CreateResponse publishMedia(
            String instagramAccountId, String accessToken, String creationId, String apiVersion) {
        URI uri = versionedUri(apiVersion).pathSegment(instagramAccountId, "media_publish").build().toUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", creationId);
        return call(
                () -> client.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "publish Instagram media");
    }

    private UriComponentsBuilder versionedUri(String apiVersion) {
        return UriComponentsBuilder.fromUriString(properties.graphBaseUrl())
                .pathSegment(resolveVersion(apiVersion));
    }

    private String resolveVersion(String apiVersion) {
        return apiVersion != null && !apiVersion.isBlank() ? apiVersion : properties.apiVersion();
    }

    private <T> T call(Supplier<T> request, String action) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            InstagramError error = parseError(body);
            log.warn(
                    "Instagram API error during [{}]: httpStatus={} code={} type={} message={}",
                    action,
                    ex.getStatusCode().value(),
                    error.code(),
                    error.type(),
                    error.message());
            throw mapInstagramError(error, ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
            log.warn("Network failure reaching Instagram during [{}]: {}", action, root.getMessage());
            throw new BusinessException("Could not reach Instagram. Try again later.", HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.warn("Failed to process Instagram response during [{}]: {}", action, ex.getMessage());
            throw new BusinessException("Instagram returned an unreadable response.", HttpStatus.BAD_GATEWAY);
        }
    }

    private RuntimeException mapInstagramError(InstagramError error, int httpStatus) {
        String message = error.message() == null ? "" : error.message();
        String lower = message.toLowerCase();
        if (httpStatus == 401 || lower.contains("invalid oauth") || lower.contains("access token")) {
            return new BusinessException(
                    "Instagram authorization expired or was revoked. Reconnect Instagram.",
                    HttpStatus.UNAUTHORIZED);
        }
        if (httpStatus == 403 || lower.contains("permission") || lower.contains("scope")) {
            return new BusinessException(
                    "Instagram permission was denied. Reconnect and approve the requested permissions.",
                    HttpStatus.FORBIDDEN);
        }
        if ((error.code() != null && error.code() == 36001)
                || lower.contains("image format")
                || lower.contains("valid image")
                || lower.contains("url returned an error page")) {
            return new BusinessException(
                    "Instagram could not read the selected media. Use a valid public direct image URL "
                            + "or select a Media Library file that can be shared publicly.",
                    HttpStatus.BAD_REQUEST);
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return new BusinessException(
                    message.isBlank() ? "Instagram rejected the request. Check your app setup." : message,
                    HttpStatus.BAD_REQUEST);
        }
        return new BusinessException("Instagram is unavailable. Try again later.", HttpStatus.BAD_GATEWAY);
    }

    private InstagramError parseError(String body) {
        if (body == null || body.isBlank()) {
            return new InstagramError(null, null, null);
        }
        try {
            InstagramErrorEnvelope envelope = objectMapper.readValue(body, InstagramErrorEnvelope.class);
            if (envelope.error() != null) {
                return envelope.error();
            }
        } catch (Exception ignored) {
            // Fall through to a body-backed message.
        }
        return new InstagramError(null, null, body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("user_id") String userId) {}

    public record ProfileResponse(
            String id,
            String username,
            String name,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("profile_picture_url") String profilePictureUrl) {}

    public record CreateResponse(String id) {}

    private record InstagramErrorEnvelope(InstagramError error) {}

    private record InstagramError(String type, Integer code, String message) {}
}
