package com.socialhub.socialhubBackend.storage.drive;

public record GoogleDriveAppCredentials(
        Long configId,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scopes) {}
