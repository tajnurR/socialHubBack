package com.socialhub.socialhubBackend.integration.core;

import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ConnectAccountCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderMetrics;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPost;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.PublishPostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.PublishResult;
import java.util.List;

/**
 * Common contract every social platform integration implements.
 *
 * <p><b>Plug-in pattern:</b> to add a platform, create a new package under
 * {@code integration.<platform>} with a {@code @Component} implementing this
 * interface (typically by extending {@link AbstractSocialMediaProvider}) and add
 * the constant to {@link SocialPlatform}. Spring discovers it and
 * {@link SocialMediaProviderRegistry} registers it automatically — no changes
 * needed anywhere else.
 */
public interface SocialMediaProvider {

    /** The platform this provider serves; used as its registry key. */
    SocialPlatform platform();

    /** Whether this provider is ready to handle requests. */
    default boolean isEnabled() {
        return true;
    }

    /** Authorize/connect an external account for an organization. */
    ProviderAccount connectAccount(ConnectAccountCommand command);

    /** Publish content to a connected account. */
    PublishResult publish(PublishPostCommand command);

    /** Fetch the most recent posts for a connected account. */
    List<ProviderPost> fetchRecentPosts(String externalAccountId, int limit);

    /** Fetch engagement metrics for a single post. */
    ProviderMetrics fetchMetrics(String externalPostId);
}
