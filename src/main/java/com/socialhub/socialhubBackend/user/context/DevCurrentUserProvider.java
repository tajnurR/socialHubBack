package com.socialhub.socialhubBackend.user.context;

import com.socialhub.socialhubBackend.config.AppProperties;
import com.socialhub.socialhubBackend.tenant.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Development implementation: returns the seeded dev user/org until SSO exists.
 *
 * <p>Honors an {@code X-Organization-Id} header (via {@link TenantContext}) so
 * tenant switching still works in dev; otherwise uses the configured defaults
 * ({@code app.tenant.default-user-id} / {@code default-organization-id}), which
 * are seeded by Flyway.
 *
 * <p>TODO[SSO]: replace with an implementation that derives the user + org from
 * the authenticated principal (and load the rest of their config from the DB).
 */
@Component
public class DevCurrentUserProvider implements CurrentUserProvider {

    private final AppProperties appProperties;

    public DevCurrentUserProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public CurrentUser currentUser() {
        Long organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            organizationId = appProperties.tenant().defaultOrganizationId();
        }
        return new CurrentUser(appProperties.tenant().defaultUserId(), organizationId);
    }
}
