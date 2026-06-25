package com.socialhub.socialhubBackend.integration.core;

import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ConnectAccountCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderMetrics;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPost;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.PublishPostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.PublishResult;
import java.util.List;

/**
 * Convenience base for providers: every operation throws "not implemented" until
 * overridden. A new platform only needs to implement {@link #platform()} to be
 * registered; real behavior is filled in operation by operation later.
 */
public abstract class AbstractSocialMediaProvider implements SocialMediaProvider {

    @Override
    public ProviderAccount connectAccount(ConnectAccountCommand command) {
        throw notImplemented("connectAccount");
    }

    @Override
    public PublishResult publish(PublishPostCommand command) {
        throw notImplemented("publish");
    }

    @Override
    public List<ProviderPost> fetchRecentPosts(String externalAccountId, int limit) {
        throw notImplemented("fetchRecentPosts");
    }

    @Override
    public ProviderMetrics fetchMetrics(String externalPostId) {
        throw notImplemented("fetchMetrics");
    }

    protected UnsupportedOperationException notImplemented(String operation) {
        return new UnsupportedOperationException(
                "%s.%s is not implemented yet".formatted(getClass().getSimpleName(), operation));
    }
}
