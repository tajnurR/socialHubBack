package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An organization's own Meta app credentials, used for the Facebook OAuth token
 * exchange. {@code appSecret} is stored <b>encrypted at rest</b> and never
 * returned to the frontend. One per organization.
 */
@Getter
@Setter
@Entity
@Table(name = "facebook_app_credentials")
public class FacebookAppCredential extends TenantBaseEntity {

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    /** Encrypted Meta app secret (ciphertext). */
    @Column(name = "app_secret", nullable = false, columnDefinition = "text")
    private String appSecret;
}
