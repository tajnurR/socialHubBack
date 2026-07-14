package com.socialhub.socialhubBackend.integration.linkedin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class LinkedInOAuthDtos {

    private LinkedInOAuthDtos() {}

    public record AuthorizationUrlRequest(@NotBlank String redirectUri, Long configId) {}

    public record AuthorizationUrlResponse(String authorizationUrl, String state, Instant expiresAt) {}

    public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state) {}

    public record LinkedInAccountOption(String id, String name, String accountType) {}

    public record ExchangeResponse(String exchangeId, List<LinkedInAccountOption> accounts, Instant tokenExpiresAt) {}

    public record ConnectAccountsRequest(@NotBlank String exchangeId, @NotEmpty List<@NotBlank String> accountIds) {}
}
