package com.socialhub.socialhubBackend.integration.linkedin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class LinkedInOAuthDtos {

    private LinkedInOAuthDtos() {}

    public record AuthorizationUrlRequest(@NotBlank String redirectUri, Long configId) {}

    public record AuthorizationUrlResponse(String authorizationUrl, String state, Instant expiresAt) {}

    public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state) {}
}
