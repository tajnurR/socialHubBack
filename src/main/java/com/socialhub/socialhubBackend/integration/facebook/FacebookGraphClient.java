package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.AccountsResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.CreateResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.Page;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.PostsResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.TokenResponse;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * Thin client over the Meta Graph API.
 *
 * <p>The access token is sent as an {@code Authorization: Bearer} header (never in
 * the URL or query string) so it cannot leak into request logs, exception messages,
 * or proxies — this lets us log failures in full without exposing the token.
 *
 * <p>Failures are categorized and logged, then surfaced as {@link BusinessException}
 * with a specific, user-actionable message:
 * <ul>
 *   <li>Facebook HTTP error → parse {@code error.code/subcode/message} and map it</li>
 *   <li>Network/timeout/SSL ({@link ResourceAccessException}) → "could not reach"</li>
 *   <li>Any other client error (e.g. response parsing) → "failed to process response"</li>
 * </ul>
 */
@Component
public class FacebookGraphClient {

    private static final Logger log = LoggerFactory.getLogger(FacebookGraphClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient client;
    private final FacebookProperties properties;

    public FacebookGraphClient(FacebookProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * GET /oauth/access_token?grant_type=fb_exchange_token — exchanges a short-lived
     * user token for a long-lived one. Uses the app id/secret (server-side only).
     */
    public TokenResponse exchangeForLongLivedUserToken(String shortLivedUserToken) {
        return call(
                () -> client.get()
                        .uri(uri -> uri.path("/oauth/access_token")
                                .queryParam("grant_type", "fb_exchange_token")
                                .queryParam("client_id", properties.appId())
                                .queryParam("client_secret", properties.appSecret())
                                .queryParam("fb_exchange_token", shortLivedUserToken)
                                .build())
                        .retrieve()
                        .body(TokenResponse.class),
                "exchange Facebook token");
    }

    /**
     * GET /{page-id}?fields=name,access_token — validates the token and resolves the
     * Page-scoped access token (Graph returns it when the caller manages the Page).
     */
    public Page getPage(String pageId, String accessToken) {
        log.debug("Validating Facebook page id={} with token={}", pageId, mask(accessToken));
        return call(
                () -> client.get()
                        .uri(uri -> uri.path("/{id}").queryParam("fields", "name,access_token").build(pageId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(Page.class),
                "validate Facebook page");
    }

    /** GET /me/accounts — pages the (user) token manages, each with its Page access token. */
    public AccountsResponse getManagedPages(String accessToken) {
        return call(
                () -> client.get()
                        .uri(uri -> uri.path("/me/accounts")
                                .queryParam("fields", "id,name,access_token")
                                .queryParam("limit", 200)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(AccountsResponse.class),
                "list managed Facebook pages");
    }

    /** GET /{page-id}/published_posts — list posts (with cursor-based pagination). */
    public PostsResponse getPublishedPosts(
            String pageId, String accessToken, String after, int limit) {
        return call(
                () -> client.get()
                        .uri(uri -> {
                            uri.path("/{id}/published_posts")
                                    .queryParam(
                                            "fields",
                                            "id,message,created_time,full_picture,permalink_url")
                                    .queryParam("limit", limit);
                            if (after != null && !after.isBlank()) {
                                uri.queryParam("after", after);
                            }
                            return uri.build(pageId);
                        })
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(PostsResponse.class),
                "list Facebook posts");
    }

    /** POST /{page-id}/feed — publish a post. */
    public CreateResponse createFeedPost(String pageId, String accessToken, String message, String link) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        if (link != null && !link.isBlank()) {
            form.add("link", link);
        }
        return call(
                () -> client.post()
                        .uri(uri -> uri.path("/{id}/feed").build(pageId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook post");
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private <T> T call(Supplier<T> request, String action) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            // Facebook returned an HTTP error with a JSON error body — map it precisely.
            String body = ex.getResponseBodyAsString();
            GraphError error = parseError(body);
            log.warn(
                    "Facebook API error during [{}]: httpStatus={} code={} subcode={} type={} message={}",
                    action,
                    ex.getStatusCode().value(),
                    error.code(),
                    error.subcode(),
                    error.type(),
                    error.message());
            throw mapGraphError(error, ex.getStatusCode().value(), action);
        } catch (ResourceAccessException ex) {
            // Could not get an HTTP response at all: DNS, connect/read timeout, SSL, proxy.
            Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
            log.error("Network failure reaching Facebook during [{}]", action, root);
            throw new BusinessException(
                    "Could not reach Facebook to " + action + " ("
                            + root.getClass().getSimpleName() + ": " + root.getMessage()
                            + "). Check the server's outbound network, DNS and proxy settings.",
                    HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            // Reached Facebook but could not process the response (e.g. body parsing).
            log.error("Failed to process Facebook response during [{}]", action, ex);
            throw new BusinessException(
                    "Failed to process Facebook's response while trying to " + action
                            + ". Please try again.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    /** Maps a parsed Graph error to a specific, user-facing exception. */
    private BusinessException mapGraphError(GraphError error, int httpStatus, String action) {
        Integer code = error.code();
        Integer subcode = error.subcode();
        String fbMessage = error.message() != null ? error.message() : "unknown error";
        String lower = fbMessage.toLowerCase();

        // Auth failures (invalid/expired/revoked token) → signal re-authentication.
        boolean tokenExpired = subcode != null && (subcode == 463 || subcode == 467);
        if (httpStatus == 401 || (code != null && code == 190)) {
            if (tokenExpired) {
                return new ProviderAuthException(
                        "Your Facebook access has expired. Reconnect the integration.");
            }
            if (lower.contains("decrypt") || lower.contains("cannot parse")
                    || lower.contains("malformed")) {
                return new ProviderAuthException(
                        "The access token looks malformed. Reconnect, or paste the exact Page "
                                + "access token (no surrounding spaces or quotes).");
            }
            return new ProviderAuthException(
                    "Facebook rejected the stored token (invalid or expired). Reconnect the "
                            + "integration.");
        }

        String message;
        if (code != null && code == 210) {
            message = "Facebook needs a Page access token for this action. Reconnect the "
                    + "integration using a Page access token (or a User token that manages "
                    + "this Page so a Page token can be derived).";
        } else if (code != null && (code == 10 || code == 200 || code == 3)) {
            message = "The token is missing permissions for this Page "
                    + "(needs pages_read_engagement and pages_manage_posts).";
        } else if (code != null && code == 100) {
            message = "Could not access that Page. Check the Page ID is correct and that the "
                    + "token is a Page access token that can manage this Page.";
        } else {
            message = "Facebook rejected the request: " + fbMessage;
        }
        return new BusinessException(message, HttpStatus.BAD_REQUEST);
    }

    /** Masks a token for logs: first/last 4 chars only. */
    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return "<empty>";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "****(len=" + trimmed.length() + ")";
        }
        return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4)
                + "(len=" + trimmed.length() + ")";
    }

    // --- Graph error parsing (regex-based to stay decoupled from the JSON library) ---

    private record GraphError(Integer code, Integer subcode, String type, String message) {}

    private static final Pattern MESSAGE = stringField("message");
    private static final Pattern TYPE = stringField("type");
    private static final Pattern CODE = numberField("code");
    private static final Pattern SUBCODE = numberField("error_subcode");

    private GraphError parseError(String body) {
        if (body == null || body.isBlank()) {
            return new GraphError(null, null, null, null);
        }
        return new GraphError(
                intField(CODE, body), intField(SUBCODE, body), strField(TYPE, body), strField(MESSAGE, body));
    }

    private static Pattern stringField(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    }

    private static Pattern numberField(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
    }

    private String strField(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer intField(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
