package com.socialhub.socialhubBackend.integration.core;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostPage;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import java.util.Map;

/**
 * Convenience base for providers: operations throw "not implemented" until
 * overridden, so a new platform only needs {@link #platform()} to be registered.
 * Also provides credential-extraction helpers.
 */
public abstract class AbstractSocialMediaProvider implements SocialMediaProvider {

    @Override
    public ProviderAccount validateCredentials(Map<String, String> credentials) {
        throw notImplemented("validateCredentials");
    }

    @Override
    public ProviderPostPage getPosts(
            String externalAccountId, String accessToken, String cursor, int limit) {
        throw notImplemented("getPosts");
    }

    @Override
    public ProviderPostRef createPost(
            String externalAccountId, String accessToken, CreatePostCommand command) {
        throw notImplemented("createPost");
    }

    /** Reads a required credential, throwing a 400 if missing/blank. */
    protected String required(Map<String, String> credentials, String key) {
        String value = credentials == null ? null : credentials.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException("Missing required credential: " + key);
        }
        return value;
    }

    protected UnsupportedOperationException notImplemented(String operation) {
        return new UnsupportedOperationException(
                "%s.%s is not implemented yet".formatted(getClass().getSimpleName(), operation));
    }
}
