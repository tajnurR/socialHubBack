package com.socialhub.socialhubBackend.tenant.service;

import com.socialhub.socialhubBackend.config.AppProperties;
import com.socialhub.socialhubBackend.tenant.context.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Resolves the organization that owns the current request.
 *
 * <p>Reads {@link TenantContext} (populated by {@code TenantContextFilter} from
 * the {@code X-Organization-Id} header for now) and falls back to the configured
 * dev default organization when absent.
 *
 * <p>TODO[SSO]: derive the organization from the authenticated principal's claims
 * and remove the header-based/default fallback.
 */
@Service
public class OrganizationContextService {

    private final AppProperties appProperties;

    public OrganizationContextService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Long currentOrganizationId() {
        Long fromContext = TenantContext.getOrganizationId();
        return fromContext != null ? fromContext : appProperties.tenant().defaultOrganizationId();
    }
}
