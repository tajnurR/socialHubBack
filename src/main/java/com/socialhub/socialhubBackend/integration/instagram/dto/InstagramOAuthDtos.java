package com.socialhub.socialhubBackend.integration.instagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class InstagramOAuthDtos {

    private InstagramOAuthDtos() {}

    public record AuthorizationUrlRequest(@NotBlank String redirectUri, Long configId) {}

    public record AuthorizationUrlResponse(String authorizationUrl, String state, Instant expiresAt) {}

    public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state) {}

    public record ExchangeRequest(@NotBlank String shortLivedToken, Long configId) {}

    public record InstagramAccountOption(
            String id,
            String name,
            String pageId,
            String pageName) {}

    public record ExchangeResponse(
            String exchangeId,
            List<InstagramAccountOption> accounts,
            Instant userTokenExpiresAt) {}

    public record ConnectRequest(@NotBlank String exchangeId, @NotBlank String accountId) {}

    public record ConnectAccountsRequest(
            @NotBlank String exchangeId,
            @NotEmpty List<@NotBlank String> accountIds) {}
}
