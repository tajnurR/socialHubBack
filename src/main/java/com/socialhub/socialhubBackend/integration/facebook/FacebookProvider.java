package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPost;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Facebook Page integration.
 *
 * <p>Credential keys (from the connect form): {@code pageId}, {@code accessToken}.
 */
@Component
public class FacebookProvider extends AbstractSocialMediaProvider {

    /** Graph returns e.g. {@code 2024-01-31T12:00:00+0000} (offset without a colon). */
    private static final Logger log = LoggerFactory.getLogger(FacebookProvider.class);

    private static final DateTimeFormatter GRAPH_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    public static final String CREDENTIAL_PAGE_ID = "pageId";
    public static final String CREDENTIAL_ACCESS_TOKEN = "accessToken";

    private final FacebookGraphClient graphClient;

    public FacebookProvider(FacebookGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.FACEBOOK;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderAccount validateCredentials(Map<String, String> credentials) {
        String pageId = required(credentials, CREDENTIAL_PAGE_ID);
        String accessToken = required(credentials, CREDENTIAL_ACCESS_TOKEN);

        GraphDtos.Page page = graphClient.getPage(pageId, accessToken, null);
        String resolvedId = page.id() != null ? page.id() : pageId;

        // Persist a Page-scoped token so page-only edges (published_posts, /feed) don't
        // fail with #210. Resolve it in order of reliability:
        //   1. the access_token echoed by GET /{pageId}?fields=access_token, then
        //   2. GET /me/accounts (canonical for a User token that manages the Page), then
        //   3. fall back to the supplied token (assume it is already a Page token).
        String pageToken = page.accessToken();
        if (isBlank(pageToken)) {
            pageToken = derivePageTokenFromAccounts(resolvedId, accessToken);
        }
        if (isBlank(pageToken)) {
            pageToken = accessToken;
        }
        return new ProviderAccount(SocialPlatform.FACEBOOK, resolvedId, page.name(), pageToken);
    }

    /**
     * Looks up the Page access token via {@code /me/accounts} (valid for a User token
     * that manages the Page). Returns {@code null} if it can't be resolved.
     */
    private String derivePageTokenFromAccounts(String pageId, String userToken) {
        try {
            GraphDtos.AccountsResponse accounts = graphClient.getManagedPages(userToken, null);
            if (accounts != null && accounts.data() != null) {
                return accounts.data().stream()
                        .filter(p -> pageId.equals(p.id()))
                        .map(GraphDtos.Page::accessToken)
                        .filter(token -> !isBlank(token))
                        .findFirst()
                        .orElse(null);
            }
        } catch (RuntimeException ex) {
            // /me/accounts is not valid for a Page token, or the token lacks pages_show_list.
            log.debug("Could not derive page token from /me/accounts for page {}: {}",
                    pageId, ex.getMessage());
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Maps a Graph post (with engagement summaries) to the platform-agnostic DTO. */
    public ProviderPost toProviderPost(GraphDtos.Post post) {
        return new ProviderPost(
                post.id(),
                post.message(),
                parseTime(post.createdTime()),
                post.fullPicture(),
                post.permalinkUrl(),
                post.likeCount(),
                post.commentCount(),
                post.shareCount(),
                post.reactionCount());
    }

    @Override
    public ProviderPostRef createPost(
            String externalAccountId, String accessToken, CreatePostCommand command) {
        if (command.message() == null || command.message().isBlank()) {
            throw new BusinessException("Post message is required");
        }
        GraphDtos.CreateResponse response = graphClient.createFeedPost(
                externalAccountId, accessToken, command.message(), command.link(), null);
        return new ProviderPostRef(response.id());
    }

    private Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, GRAPH_TIME).toInstant();
        } catch (Exception ex) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
