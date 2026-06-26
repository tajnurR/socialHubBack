package com.socialhub.socialhubBackend.tenant.service;

import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import org.springframework.stereotype.Service;

/**
 * Convenience accessor for the current request's organization id.
 *
 * <p>Delegates to {@link CurrentUserProvider} — the single source of truth for
 * the current user/org. Kept so existing callers don't all depend on
 * {@code CurrentUserProvider} directly; for the user id, inject the provider.
 */
@Service
public class OrganizationContextService {

    private final CurrentUserProvider currentUserProvider;

    public OrganizationContextService(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public Long currentOrganizationId() {
        return currentUserProvider.currentUser().organizationId();
    }
}
