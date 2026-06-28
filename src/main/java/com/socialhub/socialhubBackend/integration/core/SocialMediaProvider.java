package com.socialhub.socialhubBackend.integration.core;

import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import java.util.Map;

/**
 * Common contract every social platform integration implements.
 *
 * <p><b>Plug-in pattern:</b> to add a platform, create a package under
 * {@code integration.<platform>} with a {@code @Component} implementing this
 * interface (typically by extending {@link AbstractSocialMediaProvider}) and add
 * the constant to {@link SocialPlatform}. {@link SocialMediaProviderRegistry}
 * discovers it automatically — no shared code changes.
 */
public interface SocialMediaProvider {

    /** The platform this provider serves; its registry key. */
    SocialPlatform platform();

    /** Whether this provider is ready for use (disabled platforms show as "coming soon"). */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Validate raw, user-supplied credentials by calling the platform (e.g. fetch
     * the account name). Returns the normalized account info to persist, including
     * the access token to store.
     *
     * @param credentials platform-specific key/value pairs from the connect form
     *                    (e.g. {@code pageId}, {@code accessToken} for Facebook)
     */
    ProviderAccount validateCredentials(Map<String, String> credentials);

    /** Create a post on a connected account. */
    ProviderPostRef createPost(String externalAccountId, String accessToken, CreatePostCommand command);
}
