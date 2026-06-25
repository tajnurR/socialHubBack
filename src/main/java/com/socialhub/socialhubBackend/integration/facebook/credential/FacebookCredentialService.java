package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookGraphClient;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialStatus;
import com.socialhub.socialhubBackend.tenant.service.OrganizationContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and validates an organization's Meta app credentials.
 *
 * <p>On save, the credentials are validated against Graph (a client-credentials
 * app token call); only if valid are they persisted (App Secret encrypted). One
 * record per org — re-submitting updates it in place.
 */
@Service
@Transactional(readOnly = true)
public class FacebookCredentialService {

    private final FacebookAppCredentialRepository repository;
    private final FacebookGraphClient graphClient;
    private final EncryptionService encryptionService;
    private final OrganizationContextService organizationContext;

    public FacebookCredentialService(
            FacebookAppCredentialRepository repository,
            FacebookGraphClient graphClient,
            EncryptionService encryptionService,
            OrganizationContextService organizationContext) {
        this.repository = repository;
        this.graphClient = graphClient;
        this.encryptionService = encryptionService;
        this.organizationContext = organizationContext;
    }

    public CredentialStatus status() {
        return repository
                .findByOrganizationId(organizationContext.currentOrganizationId())
                .map(c -> CredentialStatus.configured(c.getAppId()))
                .orElseGet(CredentialStatus::notConfigured);
    }

    @Transactional
    public CredentialStatus saveOrUpdate(String appId, String appSecret) {
        // Validate against Graph before storing; throws BusinessException if invalid.
        graphClient.validateAppCredentials(appId, appSecret);

        Long organizationId = organizationContext.currentOrganizationId();
        FacebookAppCredential credential = repository
                .findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    FacebookAppCredential created = new FacebookAppCredential();
                    created.setOrganizationId(organizationId);
                    return created;
                });
        credential.setAppId(appId);
        credential.setAppSecret(encryptionService.encrypt(appSecret));
        repository.save(credential);
        return CredentialStatus.configured(appId);
    }
}
