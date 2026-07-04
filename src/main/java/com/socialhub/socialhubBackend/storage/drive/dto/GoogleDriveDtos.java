package com.socialhub.socialhubBackend.storage.drive.dto;

import com.socialhub.socialhubBackend.storage.drive.domain.DriveConnectionStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class GoogleDriveDtos {

    private GoogleDriveDtos() {}

    public record AuthorizationUrlRequest(@NotBlank String redirectUri, Long configId) {}

    public record AuthorizationUrlResponse(String authorizationUrl, String state, Instant expiresAt) {}

    public record OAuthCallbackRequest(@NotBlank String code, @NotBlank String state) {}

    public record DriveConnectionResponse(
            boolean connected,
            String googleAccountName,
            String googleAccountEmail,
            DriveConnectionStatus status,
            Instant connectedAt,
            Instant lastSyncAt) {}

    public record TestConnectionResponse(
            boolean ok,
            String message,
            Instant testedAt,
            DriveQuota quota) {}

    public record DriveQuota(Long limitBytes, Long usageBytes, boolean full) {}

    public record DriveFileResponse(
            String id,
            String name,
            String mimeType,
            String webViewLink,
            String webContentLink,
            String thumbnailLink,
            Long size,
            Instant createdTime) {}

    public record DriveFilesResponse(List<DriveFileResponse> files) {}

    public record CredentialConfigRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            String label,
            String redirectUri,
            String scopes) {}

    public record CredentialConfigResponse(
            Long id,
            String label,
            String clientId,
            String clientSecretMasked,
            String redirectUri,
            String scopes) {}
}
