package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookGraphClient;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialStatus;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and validates a user's Meta app credentials.
 *
 * <p>On save, the credentials are validated against Graph (a client-credentials
 * app token call); only if valid are they persisted (App Secret encrypted).
 * Operates on the user's primary app config (the single-config UI); the data
 * model supports multiple configs per user.
 */
@Service
@Transactional(readOnly = true)
public class FacebookCredentialService {

    private final FacebookAppCredentialRepository repository;
    private final FacebookGraphClient graphClient;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public FacebookCredentialService(
            FacebookAppCredentialRepository repository,
            FacebookGraphClient graphClient,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.graphClient = graphClient;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    public CredentialStatus status() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findFirstByOrganizationIdAndUserIdOrderByIdAsc(user.organizationId(), user.userId())
                .map(c -> CredentialStatus.configured(c.getAppId()))
                .orElseGet(CredentialStatus::notConfigured);
    }

    @Transactional
    public CredentialStatus saveOrUpdate(String appId, String appSecret) {
        // Validate against Graph (global default version) before storing.
        graphClient.validateAppCredentials(appId, appSecret, null);

        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = repository
                .findFirstByOrganizationIdAndUserIdOrderByIdAsc(user.organizationId(), user.userId())
                .orElseGet(() -> {
                    FacebookAppCredential created = new FacebookAppCredential();
                    created.setOrganizationId(user.organizationId());
                    created.setUserId(user.userId());
                    return created;
                });
        credential.setAppId(appId);
        credential.setAppSecret(encryptionService.encrypt(appSecret));
        repository.save(credential);
        return CredentialStatus.configured(appId);
    }
}
