package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A Meta app configuration owned by a user (within an organization), used for the
 * Facebook OAuth token exchange. A user may have multiple of these (multiple
 * App ID/Secret sets); each can back multiple connected pages.
 *
 * <p>{@code appSecret} is stored <b>encrypted at rest</b> and never returned to
 * the frontend. {@code apiVersion} optionally overrides the global Graph version.
 */
@Getter
@Setter
@Entity
@Table(name = "facebook_app_credentials")
public class FacebookAppCredential extends TenantBaseEntity {

    /** Owning user (within the organization). */
    @Column(name = "user_id")
    private Long userId;

    /** Optional human label to distinguish multiple app configs. */
    @Column(length = 120)
    private String label;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    /** Encrypted Meta app secret (ciphertext). */
    @Column(name = "app_secret", nullable = false, columnDefinition = "text")
    private String appSecret;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(length = 500)
    private String scopes;

    /** Per-config Graph API version override; null → use the global default. */
    @Column(name = "api_version", length = 20)
    private String apiVersion;
}
