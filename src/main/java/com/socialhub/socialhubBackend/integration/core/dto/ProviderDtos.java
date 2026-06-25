package com.socialhub.socialhubBackend.integration.core.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import java.time.Instant;
import java.util.List;

/**
 * Platform-agnostic DTOs exchanged with {@code SocialMediaProvider} implementations.
 *
 * <p>Raw, platform-specific credentials (a key/value map) are only used at connect
 * time. After validation everything is normalized to an {@code externalAccountId}
 * + {@code accessToken} pair, so the rest of the system stays platform-agnostic.
 */
public final class ProviderDtos {

    private ProviderDtos() {}

    /**
     * Result of validating credentials against the platform. Carries the
     * normalized identifiers to persist (the {@code accessToken} here is the
     * plaintext token the service will encrypt before storing).
     */
    public record ProviderAccount(
            SocialPlatform platform, String externalAccountId, String displayName, String accessToken) {}

    /** Command to create a post. {@code link} optional; media fields added later. */
    public record CreatePostCommand(String message, String link) {}

    /** A post as represented by the external platform. */
    public record ProviderPost(
            String externalId,
            String message,
            Instant createdTime,
            String fullPicture,
            String permalinkUrl) {}

    /** A page of posts plus an opaque cursor for the next page (null if none). */
    public record ProviderPostPage(List<ProviderPost> posts, String nextCursor) {}

    /** Reference to a newly created post. */
    public record ProviderPostRef(String externalPostId) {}
}
