package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.tenant.service.OrganizationContextService;
import org.springframework.stereotype.Component;

/**
 * Per-organization credential resolution: reads the org's stored Meta app
 * credentials and decrypts the secret. Active implementation of
 * {@link FacebookAppCredentialProvider}.
 */
@Component
public class PerOrgFacebookAppCredentialProvider implements FacebookAppCredentialProvider {

    private final FacebookAppCredentialRepository repository;
    private final EncryptionService encryptionService;
    private final OrganizationContextService organizationContext;

    public PerOrgFacebookAppCredentialProvider(
            FacebookAppCredentialRepository repository,
            EncryptionService encryptionService,
            OrganizationContextService organizationContext) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.organizationContext = organizationContext;
    }

    @Override
    public FacebookAppCredentials resolve() {
        Long organizationId = organizationContext.currentOrganizationId();
        FacebookAppCredential credential = repository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new BusinessException(
                        "Add your Facebook app credentials (App ID and App Secret) before connecting."));
        return new FacebookAppCredentials(
                credential.getAppId(), encryptionService.decrypt(credential.getAppSecret()));
    }

    @Override
    public boolean isConfigured() {
        return repository
                .findByOrganizationId(organizationContext.currentOrganizationId())
                .isPresent();
    }
}
