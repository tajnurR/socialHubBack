package com.socialhub.socialhubBackend.storage.drive.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "google_drive_integrations")
public class GoogleDriveIntegration extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "google_account_id", length = 255)
    private String googleAccountId;

    @Column(name = "google_account_name", length = 255)
    private String googleAccountName;

    @Column(name = "google_account_email", length = 255)
    private String googleAccountEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DriveConnectionStatus status = DriveConnectionStatus.DISCONNECTED;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "token_type", length = 40)
    private String tokenType;

    @Column(columnDefinition = "text")
    private String scopes;

    @Column(name = "token_obtained_at")
    private Instant tokenObtainedAt;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "app_credential_id")
    private Long appCredentialId;
}
