package com.socialhub.socialhubBackend.tenant.domain;

import com.socialhub.socialhubBackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A tenant. The root of data isolation: every tenant-scoped record references an
 * organization via {@code organization_id} (see {@code TenantBaseEntity}).
 */
@Getter
@Setter
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;
}
