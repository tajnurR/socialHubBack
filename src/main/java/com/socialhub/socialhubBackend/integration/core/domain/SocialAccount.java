package com.socialhub.socialhubBackend.integration.core.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A social platform account connected by an organization. The link between a
 * tenant and an external account a provider operates on.
 */
@Getter
@Setter
@Entity
@Table(name = "social_accounts")
public class SocialAccount extends TenantBaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SocialPlatform platform;

    @Column(name = "external_account_id", nullable = false, length = 255)
    private String externalAccountId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status = AccountStatus.PENDING;
}
