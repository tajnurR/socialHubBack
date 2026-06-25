package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPost;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostPage;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Facebook Page integration.
 *
 * <p>Credential keys (from the connect form): {@code pageId}, {@code accessToken}.
 */
@Component
public class FacebookProvider extends AbstractSocialMediaProvider {

    /** Graph returns e.g. {@code 2024-01-31T12:00:00+0000} (offset without a colon). */
    private static final DateTimeFormatter GRAPH_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 50;

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

        GraphDtos.Page page = graphClient.getPage(pageId, accessToken);
        String resolvedId = page.id() != null ? page.id() : pageId;
        return new ProviderAccount(SocialPlatform.FACEBOOK, resolvedId, page.name(), accessToken);
    }

    @Override
    public ProviderPostPage getPosts(
            String externalAccountId, String accessToken, String cursor, int limit) {
        int effectiveLimit = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT);
        GraphDtos.PostsResponse response =
                graphClient.getPublishedPosts(externalAccountId, accessToken, cursor, effectiveLimit);

        List<GraphDtos.Post> data = response.data() != null ? response.data() : List.of();
        List<ProviderPost> posts = data.stream()
                .map(p -> new ProviderPost(
                        p.id(), p.message(), parseTime(p.createdTime()), p.fullPicture(), p.permalinkUrl()))
                .toList();

        String nextCursor = response.paging() != null && response.paging().cursors() != null
                ? response.paging().cursors().after()
                : null;
        return new ProviderPostPage(posts, nextCursor);
    }

    @Override
    public ProviderPostRef createPost(
            String externalAccountId, String accessToken, CreatePostCommand command) {
        if (command.message() == null || command.message().isBlank()) {
            throw new BusinessException("Post message is required");
        }
        GraphDtos.CreateResponse response = graphClient.createFeedPost(
                externalAccountId, accessToken, command.message(), command.link());
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
