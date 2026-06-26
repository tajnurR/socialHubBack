package com.socialhub.socialhubBackend.integration.facebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

/** Request/response DTOs for the Facebook OAuth connect flow. */
public final class FacebookOAuthDtos {

    private FacebookOAuthDtos() {}

    /** Short-lived user token from the FB JS SDK login, sent to the backend to exchange. */
    public record ExchangeRequest(@NotBlank String shortLivedToken, Long configId) {}

    /** A page the user can choose to connect (no token exposed). */
    public record PageOption(String id, String name) {}

    /** Result of the exchange: an opaque id + the pages to choose from. */
    public record ExchangeResponse(
            String exchangeId, List<PageOption> pages, Instant userTokenExpiresAt) {}

    /** Connect a chosen page from a prior exchange. */
    public record ConnectRequest(@NotBlank String exchangeId, @NotBlank String pageId) {}

    /** Connect one or more chosen pages from a prior exchange. */
    public record ConnectPagesRequest(@NotBlank String exchangeId, @NotEmpty List<@NotBlank String> pageIds) {}

    /** Re-authenticate an existing integration using a fresh exchange. */
    public record ReauthRequest(@NotBlank String exchangeId) {}
}
