package com.socialhub.socialhubBackend.integration.facebook.credential;

import jakarta.validation.constraints.NotBlank;

/** Request/response DTOs for managing per-org Facebook app credentials. */
public final class FacebookCredentialDtos {

    private FacebookCredentialDtos() {}

    /** Submit the org's Meta app credentials (validated against Graph, then stored). */
    public record CredentialRequest(@NotBlank String appId, @NotBlank String appSecret) {}

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
