package com.socialhub.socialhubBackend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base for tenant-scoped entities. Adds the owning {@code organization_id} so
 * data can be isolated per tenant/organization.
 *
 * <p>Entities that belong to a single organization should extend this instead
 * of {@link BaseEntity}. The {@link tenant context} can later be used to
 * automatically filter queries by the current tenant.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantBaseEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
}
