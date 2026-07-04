package com.socialhub.socialhubBackend.storage.drive;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.storage.drive.domain.GoogleDriveAppCredential;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.storage.drive.repository.GoogleDriveAppCredentialRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleDriveCredentialService {

    private final GoogleDriveAppCredentialRepository repository;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleDriveCredentialService(
            GoogleDriveAppCredentialRepository repository,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    public List<CredentialConfigResponse> listConfigs() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository.findByOrganizationIdAndUserId(user.organizationId(), user.userId()).stream()
                .sorted(Comparator.comparing(GoogleDriveAppCredential::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CredentialConfigResponse createConfig(CredentialConfigRequest request) {
        validateRedirectUri(request.redirectUri());
        CurrentUser user = currentUserProvider.currentUser();
        GoogleDriveAppCredential credential = new GoogleDriveAppCredential();
        credential.setOrganizationId(user.organizationId());
        credential.setUserId(user.userId());
        credential.setClientId(request.clientId().trim());
        credential.setClientSecret(encryptionService.encrypt(request.clientSecret()));
        credential.setLabel(blankToNull(request.label()));
        credential.setRedirectUri(blankToNull(request.redirectUri()));
        credential.setScopes(blankToNull(request.scopes()));
        return toResponse(repository.save(credential));
    }

    public GoogleDriveAppCredentials resolve(Long configId) {
        CurrentUser user = currentUserProvider.currentUser();
        GoogleDriveAppCredential credential = configId == null
                ? repository.findFirstByOrganizationIdAndUserIdOrderByIdAsc(user.organizationId(), user.userId())
                        .orElseThrow(() -> new BusinessException(
                                "Add your Google Drive OAuth client credentials before connecting."))
                : repository.findByIdAndOrganizationIdAndUserId(configId, user.organizationId(), user.userId())
                        .orElseThrow(() -> new BusinessException(
                                "Google Drive app configuration not found: " + configId));
        return new GoogleDriveAppCredentials(
                credential.getId(),
                credential.getClientId(),
                encryptionService.decrypt(credential.getClientSecret()),
                credential.getRedirectUri(),
                credential.getScopes());
    }

    private CredentialConfigResponse toResponse(GoogleDriveAppCredential credential) {
        return new CredentialConfigResponse(
                credential.getId(),
                credential.getLabel(),
                credential.getClientId(),
                "********",
                credential.getRedirectUri(),
                credential.getScopes());
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
            throw new BusinessException("Invalid Google Drive redirect URI.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
