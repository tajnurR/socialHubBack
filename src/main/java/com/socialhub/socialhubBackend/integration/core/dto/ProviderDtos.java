package com.socialhub.socialhubBackend.integration.core.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import java.time.Instant;
import java.util.Map;

/**
 * Platform-agnostic DTOs exchanged with {@code SocialMediaProvider}
 * implementations. Grouped here so the common contract lives in one place;
 * split into separate files if they grow.
 */
public final class ProviderDtos {

    private ProviderDtos() {}

    /** Result of authorizing/connecting an external account. */
    public record ProviderAccount(
            SocialPlatform platform, String externalAccountId, String displayName) {}

    /** Command to connect an external account (credentials are platform-specific). */
    public record ConnectAccountCommand(Long organizationId, Map<String, String> credentials) {}

    /** Command to publish content to a connected account. */
    public record PublishPostCommand(String externalAccountId, String content) {}

    /** Outcome of a publish attempt. */
    public record PublishResult(String externalPostId, boolean accepted, String message) {}

    /** A post as represented by the external platform. */
    public record ProviderPost(String externalPostId, String content, Instant publishedAt) {}

    /** Engagement metrics for a single post. */
    public record ProviderMetrics(
            String externalPostId, long impressions, long likes, long comments, long shares) {}
}
