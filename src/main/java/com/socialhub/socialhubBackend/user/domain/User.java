package com.socialhub.socialhubBackend.user.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A user belonging to an organization.
 *
 * <p>Authentication is not implemented yet. {@code externalSubject} is the
 * extension point for SSO: it will hold the OIDC {@code sub} claim once an
 * identity provider is wired (TODO[SSO]).
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends TenantBaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.MEMBER;

    /** OIDC subject identifier; populated when SSO is wired. */
    @Column(name = "external_subject", length = 255)
    private String externalSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;
}
