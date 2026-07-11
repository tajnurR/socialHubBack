package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.AccountsResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.CreateResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.InstagramAccount;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.Page;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.PageProfile;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.PostsResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos.TokenResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpEntity;
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
        // Base URL is the Graph host only; the version is part of each path (per-call,
        // so a per-config override can be applied — see resolveVersion).
        this.client = RestClient.builder()
                .baseUrl(properties.graphBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * GET /oauth/access_token?grant_type=client_credentials — validates an app's
     * id/secret by fetching an app access token. Throws if the credentials are invalid.
     */
    public void validateAppCredentials(String appId, String appSecret, String apiVersion) {
        String path = FacebookGraphApi.OAUTH_ACCESS_TOKEN.path(resolveVersion(apiVersion));
        call(
                () -> client.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("grant_type", "client_credentials")
                                .queryParam("client_id", appId)
                                .queryParam("client_secret", appSecret)
                                .build())
                        .retrieve()
                        .body(TokenResponse.class),
                "validate Facebook app credentials");
    }

    /**
     * GET /oauth/access_token?grant_type=fb_exchange_token — exchanges a short-lived
     * user token for a long-lived one, using the supplied (per-config) app id/secret.
     */
    public TokenResponse exchangeForLongLivedUserToken(
            String shortLivedUserToken, String appId, String appSecret, String apiVersion) {
        String path = FacebookGraphApi.OAUTH_ACCESS_TOKEN.path(resolveVersion(apiVersion));
        return call(
                () -> client.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("grant_type", "fb_exchange_token")
                                .queryParam("client_id", appId)
                                .queryParam("client_secret", appSecret)
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
    public Page getPage(String pageId, String accessToken, String apiVersion) {
        log.debug("Validating Facebook page id={} with token={}", pageId, mask(accessToken));
        String path = FacebookGraphApi.PAGE.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        return call(
                () -> client.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("fields", FacebookGraphApi.Fields.PAGE_VALIDATE)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(Page.class),
                "validate Facebook page");
    }

    /** GET /me/accounts — all pages the (user) token manages, each with its Page access token. */
    public AccountsResponse getManagedPages(String accessToken, String apiVersion) {
        return getManagedPages(accessToken, apiVersion, FacebookGraphApi.Fields.MANAGED_PAGES, "list managed Facebook pages");
    }

    /** GET /me/accounts — pages and linked Instagram Business/Creator accounts. */
    public AccountsResponse getManagedPagesWithInstagram(String accessToken, String apiVersion) {
        return getManagedPages(
                accessToken,
                apiVersion,
                FacebookGraphApi.Fields.MANAGED_PAGES_WITH_INSTAGRAM,
                "list managed Instagram accounts");
    }

    private AccountsResponse getManagedPages(
            String accessToken, String apiVersion, String fields, String action) {
        List<Page> pages = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String after = null;
        boolean hasNext;
        AccountsResponse response;
        do {
            response = getManagedPagesPage(accessToken, apiVersion, after, fields, action);
            if (response != null && response.data() != null) {
                pages.addAll(response.data());
            }
            hasNext = response != null
                    && response.paging() != null
                    && response.paging().next() != null
                    && !response.paging().next().isBlank();
            after = response != null
                    && response.paging() != null
                    && response.paging().cursors() != null
                    ? response.paging().cursors().after()
                    : null;
        } while (hasNext && after != null && !after.isBlank() && seenCursors.add(after));
        return new AccountsResponse(pages, null);
    }

    private AccountsResponse getManagedPagesPage(
            String accessToken, String apiVersion, String after, String fields, String action) {
        String path = FacebookGraphApi.ME_ACCOUNTS.path(resolveVersion(apiVersion));
        return call(
                () -> client.get()
                        .uri(uri -> {
                            uri.path(path)
                                    .queryParam("fields", fields)
                                    .queryParam("limit", 200);
                            if (after != null && !after.isBlank()) {
                                uri.queryParam("after", after);
                            }
                            return uri.build();
                        })
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(AccountsResponse.class),
                action);
    }

    /** GET /{ig-user-id}?fields=id,username,name — validates an Instagram account token. */
    public InstagramAccount getInstagramAccount(
            String instagramAccountId, String accessToken, String apiVersion) {
        String path = FacebookGraphApi.INSTAGRAM_ACCOUNT.path(
                resolveVersion(apiVersion), Map.of("instagramAccountId", instagramAccountId));
        return call(
                () -> client.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("fields", FacebookGraphApi.Fields.INSTAGRAM_PROFILE)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(InstagramAccount.class),
                "validate Instagram account");
    }

    /**
     * GET /{page-id}?fields=name,fan_count,picture,category — page profile for the
     * dashboard header. Best-effort: callers should tolerate failure.
     */
    public PageProfile getPageInfo(String pageId, String accessToken, String apiVersion) {
        String path = FacebookGraphApi.PAGE.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        return call(
                () -> client.get()
                        .uri(uri -> uri.path(path)
                                .queryParam("fields", FacebookGraphApi.Fields.PAGE_PROFILE)
                                .build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(PageProfile.class),
                "load Facebook page info");
    }

    /**
     * GET /{page-id}/published_posts — list posts with engagement counts
     * (cursor pagination; optional {@code since}/{@code until} date range).
     */
    public PostsResponse getPublishedPosts(
            String pageId, String accessToken, String after, int limit,
            String since, String until, String apiVersion) {
        String path = FacebookGraphApi.PAGE_PUBLISHED_POSTS.path(
                resolveVersion(apiVersion), Map.of("pageId", pageId));
        return call(
                () -> client.get()
                        .uri(uri -> {
                            uri.path(path)
                                    .queryParam("fields", FacebookGraphApi.Fields.POST)
                                    .queryParam("limit", limit);
                            if (after != null && !after.isBlank()) {
                                uri.queryParam("after", after);
                            }
                            if (since != null && !since.isBlank()) {
                                uri.queryParam("since", since);
                            }
                            if (until != null && !until.isBlank()) {
                                uri.queryParam("until", until);
                            }
                            return uri.build();
                        })
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .retrieve()
                        .body(PostsResponse.class),
                "list Facebook posts");
    }

    /** POST /{page-id}/feed — publish a post. */
    public CreateResponse createFeedPost(
            String pageId, String accessToken, String message, String link, String apiVersion) {
        String path = FacebookGraphApi.PAGE_FEED.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        if (link != null && !link.isBlank()) {
            form.add("link", link);
        }
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook post");
    }

    /** POST /{page-id}/photos — publish a native photo post from a public URL. */
    public CreateResponse createPhotoPost(
            String pageId, String accessToken, String imageUrl, String caption, String apiVersion) {
        String path = FacebookGraphApi.PAGE_PHOTOS.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", imageUrl);
        if (caption != null && !caption.isBlank()) {
            form.add("caption", caption);
        }
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook photo post");
    }

    /** POST /{page-id}/photos — publish a native photo post from uploaded bytes. */
    public CreateResponse createPhotoPostUpload(
            String pageId,
            String accessToken,
            String filename,
            String contentType,
            byte[] bytes,
            String caption,
            String apiVersion) {
        String path = FacebookGraphApi.PAGE_PHOTOS.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        MultiValueMap<String, Object> form = multipartMediaForm(filename, contentType, bytes, caption, "caption");
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook photo post");
    }

    /** POST /{page-id}/videos — publish a native video post from a public URL. */
    public CreateResponse createVideoPost(
            String pageId, String accessToken, String videoUrl, String description, String apiVersion) {
        String path = FacebookGraphApi.PAGE_VIDEOS.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("file_url", videoUrl);
        if (description != null && !description.isBlank()) {
            form.add("description", description);
        }
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook video post");
    }

    /** POST /{page-id}/videos — publish a native video post from uploaded bytes. */
    public CreateResponse createVideoPostUpload(
            String pageId,
            String accessToken,
            String filename,
            String contentType,
            byte[] bytes,
            String description,
            String apiVersion) {
        String path = FacebookGraphApi.PAGE_VIDEOS.path(resolveVersion(apiVersion), Map.of("pageId", pageId));
        MultiValueMap<String, Object> form = multipartMediaForm(filename, contentType, bytes, description, "description");
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Facebook video post");
    }

    /** POST /{ig-user-id}/media — create an Instagram image/video container. */
    public CreateResponse createInstagramMediaContainer(
            String instagramAccountId,
            String accessToken,
            String mediaUrl,
            com.socialhub.socialhubBackend.post.domain.PostMediaType mediaType,
            String caption,
            String apiVersion) {
        String path = FacebookGraphApi.INSTAGRAM_MEDIA.path(
                resolveVersion(apiVersion), Map.of("instagramAccountId", instagramAccountId));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (mediaType == com.socialhub.socialhubBackend.post.domain.PostMediaType.VIDEO) {
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
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "create Instagram media container");
    }

    /** POST /{ig-user-id}/media_publish — publish an Instagram media container. */
    public CreateResponse publishInstagramMedia(
            String instagramAccountId, String accessToken, String creationId, String apiVersion) {
        String path = FacebookGraphApi.INSTAGRAM_MEDIA_PUBLISH.path(
                resolveVersion(apiVersion), Map.of("instagramAccountId", instagramAccountId));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creation_id", creationId);
        return call(
                () -> client.post()
                        .uri(uri -> uri.path(path).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(CreateResponse.class),
                "publish Instagram media");
    }

    /** Per-config override if provided, else the global default version. */
    private String resolveVersion(String apiVersion) {
        return apiVersion != null && !apiVersion.isBlank() ? apiVersion : properties.apiVersion();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private MultiValueMap<String, Object> multipartMediaForm(
            String filename, String contentType, byte[] bytes, String message, String messageField) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType));
        form.add("source", new HttpEntity<>(new NamedByteArrayResource(bytes, filename), fileHeaders));
        if (message != null && !message.isBlank()) {
            form.add(messageField, message);
        }
        return form;
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

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray == null ? new byte[0] : byteArray);
            this.filename = filename == null || filename.isBlank() ? "media.bin" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
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
