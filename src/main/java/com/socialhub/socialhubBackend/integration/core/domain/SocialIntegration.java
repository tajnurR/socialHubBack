package com.socialhub.socialhubBackend.integration.core.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A connected social platform account, scoped to an organization.
 *
 * <p>Holds the credentials needed to act on the account. {@code accessToken} is
 * stored <b>encrypted at rest</b> (AES-GCM via {@code EncryptionService}) and is
 * never exposed through the API — response DTOs mask it.
 */
@Getter
@Setter
@Entity
@Table(name = "social_integrations")
public class SocialIntegration extends TenantBaseEntity {

    /** Owning user (within the organization) — connections are isolated per user. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SocialPlatform platform;

    /** Platform-side account identifier (e.g. Facebook Page ID). */
    @Column(name = "external_account_id", nullable = false, length = 255)
    private String externalAccountId;

    /** Encrypted access token (ciphertext). Decrypt only when calling the platform. */
    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    private String accessToken;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationStatus status = IntegrationStatus.CONNECTED;

    /** How the token was obtained, e.g. {@code MANUAL}, {@code PAGE_OAUTH}. */
    @Column(name = "token_type", length = 40)
    private String tokenType;

    @Column(name = "token_obtained_at")
    private Instant tokenObtainedAt;

    /** When the token expires; {@code null} for non-expiring Page tokens. */
    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    /** The app config (FacebookAppCredential) this connection was created through; null for manual. */
    @Column(name = "app_credential_id")
    private Long appCredentialId;
}
