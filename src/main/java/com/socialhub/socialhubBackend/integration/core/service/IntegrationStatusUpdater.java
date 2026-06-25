package com.socialhub.socialhubBackend.integration.core.service;

import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an integration's status in its own transaction.
 *
 * <p>{@code REQUIRES_NEW} so the status change commits independently — used when a
 * read call detects an auth failure and must record {@code REAUTH_REQUIRED} while
 * still rethrowing the error to the caller (which would otherwise roll back).
 */
@Service
public class IntegrationStatusUpdater {

    private final SocialIntegrationRepository repository;

    public IntegrationStatusUpdater(SocialIntegrationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReauthRequired(Long integrationId) {
        repository.findById(integrationId).ifPresent(integration -> {
            integration.setStatus(IntegrationStatus.REAUTH_REQUIRED);
            repository.save(integration);
        });
    }
}
