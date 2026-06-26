package com.socialhub.socialhubBackend.integration.facebook.credential;

import jakarta.validation.constraints.NotBlank;

/** Request/response DTOs for managing per-user Facebook app credentials. */
public final class FacebookCredentialDtos {

    private FacebookCredentialDtos() {}

    /** Submit the user's primary Meta app credentials (validated against Graph, then stored). */
    public record CredentialRequest(@NotBlank String appId, @NotBlank String appSecret) {}

    /** Create another Meta app config for the current user. */
    public record CredentialConfigRequest(
            @NotBlank String appId,
            @NotBlank String appSecret,
            String label,
            String redirectUri,
            String scopes,
            String apiVersion) {}

    /** A Meta app config owned by the current user. The secret is never returned. */
    public record CredentialConfigResponse(
            Long id,
            String label,
            String appId,
            String appSecretMasked,
            String redirectUri,
            String scopes,
            String apiVersion) {}

    /** Status/representation — the secret is never returned, only a masked hint. */
    public record CredentialStatus(boolean configured, String appId, String appSecretMasked) {

        public static CredentialStatus notConfigured() {
            return new CredentialStatus(false, null, null);
        }

        public static CredentialStatus configured(String appId) {
            return new CredentialStatus(true, appId, "********");
        }
    }
}
