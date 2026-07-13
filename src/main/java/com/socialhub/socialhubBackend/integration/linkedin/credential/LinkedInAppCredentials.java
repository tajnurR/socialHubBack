package com.socialhub.socialhubBackend.integration.linkedin.credential;

public record LinkedInAppCredentials(
        Long configId,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scopes,
        String apiVersion) {}
