package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * Database-backed, per-user credential resolution. Reads the current user's
 * stored Meta app config(s) and decrypts the secret. Active implementation of
 * {@link FacebookAppCredentialProvider}.
 */
@Component
public class DbFacebookAppCredentialProvider implements FacebookAppCredentialProvider {

    private final FacebookAppCredentialRepository repository;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public DbFacebookAppCredentialProvider(
            FacebookAppCredentialRepository repository,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public FacebookAppCredentials resolve() {
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = repository
                .findFirstByOrganizationIdAndUserIdOrderByIdAsc(user.organizationId(), user.userId())
                .orElseThrow(() -> new BusinessException(
                        "Add your Facebook app credentials (App ID and App Secret) before connecting."));
        return toCredentials(credential);
    }

    @Override
    public FacebookAppCredentials resolveById(Long configId) {
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = repository
                .findByIdAndOrganizationIdAndUserId(configId, user.organizationId(), user.userId())
                .orElseThrow(() -> new BusinessException("Facebook app configuration not found: " + configId));
        return toCredentials(credential);
    }

    @Override
    public String apiVersionForConfig(Long configId) {
        if (configId == null) {
            return null;
        }
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByIdAndOrganizationIdAndUserId(configId, user.organizationId(), user.userId())
                .map(FacebookAppCredential::getApiVersion)
                .orElse(null);
    }

    @Override
    public boolean isConfigured() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findFirstByOrganizationIdAndUserIdOrderByIdAsc(user.organizationId(), user.userId())
                .isPresent();
    }

    private FacebookAppCredentials toCredentials(FacebookAppCredential credential) {
        return new FacebookAppCredentials(
                credential.getId(),
                credential.getAppId(),
                encryptionService.decrypt(credential.getAppSecret()),
                credential.getApiVersion());
    }
}
