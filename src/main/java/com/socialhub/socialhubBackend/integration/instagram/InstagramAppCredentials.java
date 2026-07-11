package com.socialhub.socialhubBackend.integration.instagram;

/**
 * Resolved decrypted Instagram app credentials owned by the current user.
 */
public record InstagramAppCredentials(
        Long configId,
        String appId,
        String appSecret,
        String redirectUri,
        String scopes,
        String apiVersion) {}
