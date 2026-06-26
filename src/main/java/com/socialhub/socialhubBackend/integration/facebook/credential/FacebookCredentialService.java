package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookGraphClient;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialStatus;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and validates a user's Meta app credentials.
 *
 * <p>On save, the credentials are validated against Graph (a client-credentials
 * app token call); only if valid are they persisted (App Secret encrypted).
 * The legacy status/save methods operate on the user's primary app config, and
 * the config list/create methods expose the multi-app model.
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

    public List<CredentialConfigResponse> listConfigs() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository.findByOrganizationIdAndUserId(user.organizationId(), user.userId()).stream()
                .sorted(Comparator.comparing(FacebookAppCredential::getId))
                .map(this::toResponse)
                .toList();
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

    @Transactional
    public CredentialConfigResponse createConfig(CredentialConfigRequest request) {
        graphClient.validateAppCredentials(request.appId(), request.appSecret(), request.apiVersion());

        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = new FacebookAppCredential();
        credential.setOrganizationId(user.organizationId());
        credential.setUserId(user.userId());
        credential.setAppId(request.appId());
        credential.setAppSecret(encryptionService.encrypt(request.appSecret()));
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(blankToNull(request.scopes()));
        credential.setApiVersion(blankToNull(request.apiVersion()));
        return toResponse(repository.save(credential));
    }

    private CredentialConfigResponse toResponse(FacebookAppCredential credential) {
        return new CredentialConfigResponse(
                credential.getId(),
                credential.getLabel(),
                credential.getAppId(),
                "********",
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
