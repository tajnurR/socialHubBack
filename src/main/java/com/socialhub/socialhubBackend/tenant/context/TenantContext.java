package com.socialhub.socialhubBackend.tenant.context;

/**
 * Holds the current request's tenant (organization) id in a {@link ThreadLocal}.
 *
 * <p>Populated per request by {@code TenantContextFilter} and read by services
 * that need to scope queries to the active tenant. Always {@link #clear()} at the
 * end of a request to avoid leaking state across pooled threads.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORGANIZATION = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganizationId(Long organizationId) {
        CURRENT_ORGANIZATION.set(organizationId);
    }

    public static Long getOrganizationId() {
        return CURRENT_ORGANIZATION.get();
    }

    public static void clear() {
        CURRENT_ORGANIZATION.remove();
    }
}
