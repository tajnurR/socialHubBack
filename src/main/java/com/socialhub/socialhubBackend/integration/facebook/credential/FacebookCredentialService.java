package com.socialhub.socialhubBackend.integration.facebook.credential;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.integration.facebook.FacebookGraphClient;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigUpdateRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialStatus;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.time.Instant;
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
    private final SocialIntegrationRepository integrationRepository;
    private final FacebookGraphClient graphClient;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public FacebookCredentialService(
            FacebookAppCredentialRepository repository,
            SocialIntegrationRepository integrationRepository,
            FacebookGraphClient graphClient,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.integrationRepository = integrationRepository;
        this.graphClient = graphClient;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    public CredentialStatus status() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findFirstByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                .map(c -> CredentialStatus.configured(c.getAppId()))
                .orElseGet(CredentialStatus::notConfigured);
    }

    public List<CredentialConfigResponse> listConfigs() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                .stream()
                .map(credential -> toResponse(credential, user))
                .toList();
    }

    @Transactional
    public CredentialStatus saveOrUpdate(String appId, String appSecret) {
        // Validate against Graph (global default version) before storing.
        graphClient.validateAppCredentials(appId, appSecret, null);

        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = repository
                .findFirstByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
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
        credential.setStatus(FacebookAppCredentialStatus.ACTIVE);
        credential.setDeletedAt(null);
        return toResponse(repository.save(credential), user);
    }

    @Transactional
    public CredentialConfigResponse updateConfig(Long id, CredentialConfigUpdateRequest request) {
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = getOwnedActive(id, user);
        String appSecret = blankToNull(request.appSecret());
        boolean appIdChanged = !credential.getAppId().equals(request.appId());
        if (appSecret != null) {
            graphClient.validateAppCredentials(request.appId(), appSecret, request.apiVersion());
            credential.setAppSecret(encryptionService.encrypt(appSecret));
        } else if (appIdChanged) {
            throw new BusinessException("App Secret is required when changing the Facebook App ID.");
        }
        credential.setAppId(request.appId());
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(blankToNull(request.scopes()));
        credential.setApiVersion(blankToNull(request.apiVersion()));
        return toResponse(repository.save(credential), user);
    }

    @Transactional
    public void softDeleteConfig(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = getOwnedActive(id, user);
        credential.setStatus(FacebookAppCredentialStatus.DELETED);
        credential.setDeletedAt(Instant.now());
        repository.save(credential);
    }

    private FacebookAppCredential getOwnedActive(Long id, CurrentUser user) {
        return repository
                .findByIdAndOrganizationIdAndUserIdAndStatusAndDeletedAtIsNull(
                        id, user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("Facebook app configuration not found: " + id));
    }

    private CredentialConfigResponse toResponse(FacebookAppCredential credential, CurrentUser user) {
        return new CredentialConfigResponse(
                credential.getId(),
                credential.getLabel(),
                credential.getAppId(),
                "********",
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion(),
                connected(credential, user),
                credential.getCreatedAt(),
                credential.getStatus());
    }

    private boolean connected(FacebookAppCredential credential, CurrentUser user) {
        return integrationRepository.existsByOrganizationIdAndUserIdAndPlatformAndAppCredentialIdAndStatus(
                user.organizationId(),
                user.userId(),
                SocialPlatform.FACEBOOK,
                credential.getId(),
                IntegrationStatus.CONNECTED);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
