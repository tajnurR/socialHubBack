package com.socialhub.socialhubBackend.integration.linkedin.credential;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class LinkedInCredentialDtos {

    private LinkedInCredentialDtos() {}

    public record CredentialConfigRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            String label,
            String redirectUri,
            String scopes,
            String apiVersion) {}

    public record CredentialConfigUpdateRequest(
            @NotBlank String clientId,
            String clientSecret,
            String label,
            String redirectUri,
            String scopes,
            String apiVersion) {}

    public record CredentialConfigResponse(
            Long id,
            String label,
            String clientId,
            String clientSecretMasked,
            String redirectUri,
            String scopes,
            String apiVersion,
            boolean connected,
            Instant createdAt,
            LinkedInAppCredentialStatus status) {}
}
