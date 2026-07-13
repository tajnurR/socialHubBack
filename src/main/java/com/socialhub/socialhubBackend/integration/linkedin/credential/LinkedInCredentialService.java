package com.socialhub.socialhubBackend.integration.linkedin.credential;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInProperties;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigUpdateRequest;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LinkedInCredentialService {

    private final LinkedInAppCredentialRepository repository;
    private final SocialIntegrationRepository integrationRepository;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;
    private final LinkedInProperties properties;

    public LinkedInCredentialService(
            LinkedInAppCredentialRepository repository,
            SocialIntegrationRepository integrationRepository,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider,
            LinkedInProperties properties) {
        this.repository = repository;
        this.integrationRepository = integrationRepository;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    public List<CredentialConfigResponse> listConfigs() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        user.organizationId(), user.userId(), LinkedInAppCredentialStatus.ACTIVE)
                .stream()
                .map(credential -> toResponse(credential, user))
                .toList();
    }

    @Transactional
    public CredentialConfigResponse createConfig(CredentialConfigRequest request) {
        validateRedirectUri(request.redirectUri());
        CurrentUser user = currentUserProvider.currentUser();
        LinkedInAppCredential credential = new LinkedInAppCredential();
        credential.setOrganizationId(user.organizationId());
        credential.setUserId(user.userId());
        credential.setClientId(request.clientId().trim());
        credential.setClientSecret(encryptionService.encrypt(request.clientSecret()));
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(resolveScopes(request.scopes()));
        credential.setApiVersion(blankToNull(request.apiVersion()));
        credential.setStatus(LinkedInAppCredentialStatus.ACTIVE);
        credential.setDeletedAt(null);
        return toResponse(repository.save(credential), user);
    }

    @Transactional
    public CredentialConfigResponse updateConfig(Long id, CredentialConfigUpdateRequest request) {
        validateRedirectUri(request.redirectUri());
        CurrentUser user = currentUserProvider.currentUser();
        LinkedInAppCredential credential = ownedCredential(id, user);
        credential.setClientId(request.clientId().trim());
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            credential.setClientSecret(encryptionService.encrypt(request.clientSecret()));
        }
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(resolveScopes(request.scopes()));
        credential.setApiVersion(blankToNull(request.apiVersion()));
        return toResponse(repository.save(credential), user);
    }

    @Transactional
    public void deleteConfig(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        LinkedInAppCredential credential = ownedCredential(id, user);
        credential.setStatus(LinkedInAppCredentialStatus.DELETED);
        credential.setDeletedAt(Instant.now());
        repository.save(credential);
    }

    public LinkedInAppCredentials resolve(Long configId) {
        CurrentUser user = currentUserProvider.currentUser();
        LinkedInAppCredential credential = configId == null
                ? repository
                        .findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                                user.organizationId(), user.userId(), LinkedInAppCredentialStatus.ACTIVE)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Add your LinkedIn app credentials before connecting."))
                : ownedCredential(configId, user);
        return new LinkedInAppCredentials(
                credential.getId(),
                credential.getClientId(),
                encryptionService.decrypt(credential.getClientSecret()),
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion());
    }

    private LinkedInAppCredential ownedCredential(Long id, CurrentUser user) {
        return repository
                .findByIdAndOrganizationIdAndUserIdAndStatusAndDeletedAtIsNull(
                        id, user.organizationId(), user.userId(), LinkedInAppCredentialStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("LinkedIn app configuration not found: " + id));
    }

    private CredentialConfigResponse toResponse(LinkedInAppCredential credential, CurrentUser user) {
        boolean connected = integrationRepository.existsByOrganizationIdAndUserIdAndPlatformAndAppCredentialIdAndStatus(
                user.organizationId(),
                user.userId(),
                SocialPlatform.LINKEDIN,
                credential.getId(),
                IntegrationStatus.CONNECTED);
        return new CredentialConfigResponse(
                credential.getId(),
                credential.getLabel(),
                credential.getClientId(),
                "********",
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion(),
                connected,
                credential.getCreatedAt(),
                credential.getStatus());
    }

    private String resolveScopes(String scopes) {
        String value = blankToNull(scopes);
        return value == null ? properties.joinedScopes() : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid LinkedIn redirect URI.");
        }
    }
}
