package com.socialhub.socialhubBackend.integration.instagram;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredential;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentialRepository;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentialStatus;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstagramCredentialService {

    public static final String INSTAGRAM_SCOPES =
            "instagram_business_basic,instagram_business_content_publish";

    private final FacebookAppCredentialRepository repository;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public InstagramCredentialService(
            FacebookAppCredentialRepository repository,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    public List<CredentialConfigResponse> listConfigs() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                .stream()
                .filter(this::isInstagramConfig)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CredentialConfigResponse createConfig(CredentialConfigRequest request) {
        validateRedirectUri(request.redirectUri());
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = new FacebookAppCredential();
        credential.setOrganizationId(user.organizationId());
        credential.setUserId(user.userId());
        credential.setAppId(request.appId().trim());
        credential.setAppSecret(encryptionService.encrypt(request.appSecret()));
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(blankToNull(request.scopes()) == null ? INSTAGRAM_SCOPES : blankToNull(request.scopes()));
        credential.setApiVersion(blankToNull(request.apiVersion()));
        credential.setStatus(FacebookAppCredentialStatus.ACTIVE);
        credential.setDeletedAt(null);
        return toResponse(repository.save(credential));
    }

    public InstagramAppCredentials resolve(Long configId) {
        CurrentUser user = currentUserProvider.currentUser();
        FacebookAppCredential credential = configId == null
                ? repository
                        .findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                                user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                        .stream()
                        .filter(this::isInstagramConfig)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Add your Instagram app credentials before connecting."))
                : repository
                        .findByIdAndOrganizationIdAndUserIdAndStatusAndDeletedAtIsNull(
                                configId, user.organizationId(), user.userId(), FacebookAppCredentialStatus.ACTIVE)
                        .filter(this::isInstagramConfig)
                        .orElseThrow(() -> new BusinessException(
                                "Instagram app configuration not found: " + configId));
        return new InstagramAppCredentials(
                credential.getId(),
                credential.getAppId(),
                encryptionService.decrypt(credential.getAppSecret()),
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion());
    }

    private boolean isInstagramConfig(FacebookAppCredential credential) {
        String scopes = credential.getScopes() == null ? "" : credential.getScopes().toLowerCase();
        return scopes.contains("instagram_business_basic")
                || scopes.contains("instagram_business_content_publish");
    }

    private CredentialConfigResponse toResponse(FacebookAppCredential credential) {
        return new CredentialConfigResponse(
                credential.getId(),
                credential.getLabel(),
                credential.getAppId(),
                "********",
                credential.getRedirectUri(),
                credential.getScopes(),
                credential.getApiVersion(),
                false,
                credential.getCreatedAt(),
                credential.getStatus());
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
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid Instagram redirect URI.");
        }
    }
}
