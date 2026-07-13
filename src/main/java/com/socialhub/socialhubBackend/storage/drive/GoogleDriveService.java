package com.socialhub.socialhubBackend.storage.drive;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.AboutResponse;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DriveFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DownloadedFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.FilesResponse;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.StorageQuota;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.TokenResponse;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.UserInfo;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveOAuthStateStore.StateEntry;
import com.socialhub.socialhubBackend.storage.drive.domain.DriveConnectionStatus;
import com.socialhub.socialhubBackend.storage.drive.domain.GoogleDriveIntegration;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveConnectionResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveFileResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveFilesResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveQuota;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.TestConnectionResponse;
import com.socialhub.socialhubBackend.storage.drive.repository.GoogleDriveIntegrationRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly = true)
public class GoogleDriveService {

    private static final long TOKEN_REFRESH_SKEW_SECONDS = 60;

    private final GoogleDriveProperties properties;
    private final GoogleDriveOAuthStateStore stateStore;
    private final GoogleDriveClient client;
    private final GoogleDriveCredentialService credentialService;
    private final GoogleDriveIntegrationRepository repository;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;

    public GoogleDriveService(
            GoogleDriveProperties properties,
            GoogleDriveOAuthStateStore stateStore,
            GoogleDriveClient client,
            GoogleDriveCredentialService credentialService,
            GoogleDriveIntegrationRepository repository,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.client = client;
        this.credentialService = credentialService;
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
    }

    public DriveConnectionResponse status() {
        return repository.findByOrganizationIdAndUserId(currentUser().organizationId(), currentUser().userId())
                .map(this::toConnectionResponse)
                .orElseGet(() -> new DriveConnectionResponse(
                        false, null, null, DriveConnectionStatus.DISCONNECTED, null, null));
    }

    public AuthorizationUrlResponse authorizationUrl(String redirectUri, Long configId) {
        GoogleDriveAppCredentials credentials = credentialService.resolve(configId);
        String effectiveRedirectUri = credentials.redirectUri() != null && !credentials.redirectUri().isBlank()
                ? credentials.redirectUri()
                : redirectUri;
        String scopes = credentials.scopes() != null && !credentials.scopes().isBlank()
                ? credentials.scopes()
                : properties.joinedScopes();
        validateRedirectUri(effectiveRedirectUri);
        StateEntry entry = stateStore.create(currentUser(), credentials.configId(), effectiveRedirectUri);
        String url = UriComponentsBuilder.fromUriString(properties.authBaseUrl())
                .queryParam("client_id", credentials.clientId())
                .queryParam("redirect_uri", effectiveRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", entry.state())
                .build()
                .toUriString();
        return new AuthorizationUrlResponse(url, entry.state(), entry.expiresAt());
    }

    @Transactional
    public DriveConnectionResponse connect(String code, String state) {
        StateEntry entry = stateStore.consume(state, currentUser());
        GoogleDriveAppCredentials credentials = credentialService.resolve(entry.configId());
        TokenResponse token = client.exchangeAuthorizationCode(
                code, entry.redirectUri(), credentials.clientId(), credentials.clientSecret());
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw new BusinessException("Google OAuth failed: no access token was returned.");
        }

        GoogleDriveIntegration integration = ownedOrNew();
        String refreshToken = token.refreshToken();
        if ((refreshToken == null || refreshToken.isBlank()) && integration.getRefreshToken() == null) {
            throw new BusinessException(
                    "Google OAuth did not return a refresh token. Try again and approve offline access.");
        }

        UserInfo userInfo = client.getUserInfo(token.accessToken());
        Instant now = Instant.now();
        integration.setGoogleAccountId(userInfo.id());
        integration.setGoogleAccountEmail(userInfo.email());
        integration.setGoogleAccountName(userInfo.name());
        integration.setStatus(DriveConnectionStatus.CONNECTED);
        integration.setAccessToken(encryptionService.encrypt(token.accessToken()));
        if (refreshToken != null && !refreshToken.isBlank()) {
            integration.setRefreshToken(encryptionService.encrypt(refreshToken));
        }
        integration.setTokenType(token.tokenType());
        integration.setScopes(token.scope() != null && !token.scope().isBlank()
                ? token.scope()
                : properties.joinedScopes());
        integration.setTokenObtainedAt(now);
        integration.setAccessTokenExpiresAt(expiresAt(token));
        integration.setConnectedAt(now);
        integration.setLastSyncAt(now);
        integration.setAppCredentialId(credentials.configId());
        return toConnectionResponse(repository.save(integration));
    }

    @Transactional
    public DriveConnectionResponse disconnect() {
        GoogleDriveIntegration integration = ownedOrNew();
        integration.setStatus(DriveConnectionStatus.DISCONNECTED);
        integration.setAccessToken(null);
        integration.setRefreshToken(null);
        integration.setTokenType(null);
        integration.setTokenObtainedAt(null);
        integration.setAccessTokenExpiresAt(null);
        return toConnectionResponse(repository.save(integration));
    }

    @Transactional
    public TestConnectionResponse testConnection() {
        AboutResponse about = withAccessToken(client::getAbout);
        DriveQuota quota = quota(about.storageQuota());
        if (quota.full()) {
            throw new BusinessException("Google Drive quota is full.", HttpStatus.INSUFFICIENT_STORAGE);
        }
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return new TestConnectionResponse(true, "Google Drive connection is working.", Instant.now(), quota);
    }

    @Transactional
    public DriveFileResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a media file to upload.");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new BusinessException("Only image and video files can be uploaded for social posts.");
        }
        DriveFile uploaded = withAccessToken(token -> client.uploadFile(token, file));
        return toFileResponse(uploaded);
    }

    @Transactional
    public DriveFile uploadMediaFile(MultipartFile file) {
        DriveFile uploaded = withAccessToken(token -> client.uploadFile(token, file));
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return uploaded;
    }

    @Transactional
    public DriveFile uploadMediaFile(MultipartFile file, String parentFolderId) {
        DriveFile uploaded = withAccessToken(token -> client.uploadFile(
                token,
                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                        ? "socialhub-media"
                        : file.getOriginalFilename(),
                file.getContentType() == null || file.getContentType().isBlank()
                        ? org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : file.getContentType(),
                toBytes(file),
                parentFolderId));
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return uploaded;
    }

    @Transactional
    public DriveFile uploadMediaBytes(String filename, String contentType, byte[] bytes) {
        return uploadMediaBytes(filename, contentType, bytes, null);
    }

    @Transactional
    public DriveFile uploadMediaBytes(String filename, String contentType, byte[] bytes, String parentFolderId) {
        DriveFile uploaded = withAccessToken(token -> client.uploadFile(
                token, filename, contentType, bytes, parentFolderId));
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return uploaded;
    }

    @Transactional
    public DriveFile createFolder(String name) {
        DriveFile folder = withAccessToken(token -> client.createFolder(token, name));
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return folder;
    }

    public DriveFile getMediaFile(String googleDriveFileId) {
        return withAccessToken(token -> client.getFile(token, googleDriveFileId));
    }

    public DriveFile getMediaFile(Long organizationId, Long userId, String googleDriveFileId) {
        return withAccessToken(organizationId, userId, token -> client.getFile(token, googleDriveFileId));
    }

    @Transactional
    public DriveFile makeMediaFilePublic(Long organizationId, Long userId, String googleDriveFileId) {
        DriveFile file = withAccessToken(organizationId, userId, token -> {
            client.makeFilePublic(token, googleDriveFileId);
            return client.getFile(token, googleDriveFileId);
        });
        GoogleDriveIntegration integration = ownedConnected(organizationId, userId);
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return file;
    }

    public DownloadedFile downloadMediaFile(String googleDriveFileId) {
        return withAccessToken(token -> client.downloadFile(token, googleDriveFileId));
    }

    public DownloadedFile downloadMediaFile(Long organizationId, Long userId, String googleDriveFileId) {
        return withAccessToken(organizationId, userId, token -> client.downloadFile(token, googleDriveFileId));
    }

    @Transactional
    public void deleteMediaFile(String googleDriveFileId) {
        withAccessToken(token -> {
            client.deleteFile(token, googleDriveFileId);
            return null;
        });
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
    }

    @Transactional
    public DriveFilesResponse listFiles() {
        FilesResponse response = withAccessToken(client::listFiles);
        List<DriveFileResponse> files = response == null || response.files() == null
                ? List.of()
                : response.files().stream().map(this::toFileResponse).toList();
        GoogleDriveIntegration integration = ownedConnected();
        integration.setLastSyncAt(Instant.now());
        repository.save(integration);
        return new DriveFilesResponse(files);
    }

    private <T> T withAccessToken(Function<String, T> action) {
        CurrentUser user = currentUser();
        return withAccessToken(user.organizationId(), user.userId(), action);
    }

    private <T> T withAccessToken(Long organizationId, Long userId, Function<String, T> action) {
        GoogleDriveIntegration integration = ownedConnected(organizationId, userId);
        String accessToken = validAccessToken(integration);
        try {
            return action.apply(accessToken);
        } catch (GoogleDriveAuthException ex) {
            String refreshed = refreshAccessToken(integration);
            try {
                return action.apply(refreshed);
            } catch (GoogleDriveAuthException retryEx) {
                markReauthRequired(integration);
                throw new BusinessException(
                        "Google Drive token expired or was revoked. Reconnect Google Drive.",
                        HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private String validAccessToken(GoogleDriveIntegration integration) {
        Instant expiresAt = integration.getAccessTokenExpiresAt();
        if (expiresAt != null && expiresAt.minusSeconds(TOKEN_REFRESH_SKEW_SECONDS).isBefore(Instant.now())) {
            return refreshAccessToken(integration);
        }
        return encryptionService.decrypt(integration.getAccessToken());
    }

    private String refreshAccessToken(GoogleDriveIntegration integration) {
        if (integration.getRefreshToken() == null || integration.getRefreshToken().isBlank()) {
            markReauthRequired(integration);
            throw new BusinessException("Reconnect Google Drive to restore access.", HttpStatus.UNAUTHORIZED);
        }
        try {
            String refreshToken = encryptionService.decrypt(integration.getRefreshToken());
            GoogleDriveAppCredentials credentials = credentialService.resolve(
                    integration.getOrganizationId(), integration.getUserId(), integration.getAppCredentialId());
            TokenResponse refreshed = client.refreshAccessToken(
                    refreshToken, credentials.clientId(), credentials.clientSecret());
            integration.setAccessToken(encryptionService.encrypt(refreshed.accessToken()));
            integration.setTokenType(refreshed.tokenType());
            integration.setTokenObtainedAt(Instant.now());
            integration.setAccessTokenExpiresAt(expiresAt(refreshed));
            integration.setStatus(DriveConnectionStatus.CONNECTED);
            repository.save(integration);
            return refreshed.accessToken();
        } catch (GoogleDriveAuthException ex) {
            markReauthRequired(integration);
            throw new BusinessException(
                    "Google Drive token expired or was revoked. Reconnect Google Drive.",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    private void markReauthRequired(GoogleDriveIntegration integration) {
        integration.setStatus(DriveConnectionStatus.REAUTH_REQUIRED);
        repository.save(integration);
    }

    private GoogleDriveIntegration ownedConnected() {
        CurrentUser user = currentUser();
        return ownedConnected(user.organizationId(), user.userId());
    }

    private GoogleDriveIntegration ownedConnected(Long organizationId, Long userId) {
        GoogleDriveIntegration integration = repository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Connect Google Drive before uploading media.", HttpStatus.BAD_REQUEST));
        if (integration.getStatus() != DriveConnectionStatus.CONNECTED
                || integration.getAccessToken() == null
                || integration.getAccessToken().isBlank()) {
            throw new BusinessException(
                    "Connect Google Drive before uploading media.", HttpStatus.BAD_REQUEST);
        }
        return integration;
    }

    private GoogleDriveIntegration ownedOrNew() {
        CurrentUser user = currentUser();
        return repository.findByOrganizationIdAndUserId(user.organizationId(), user.userId())
                .orElseGet(() -> {
                    GoogleDriveIntegration created = new GoogleDriveIntegration();
                    created.setOrganizationId(user.organizationId());
                    created.setUserId(user.userId());
                    return created;
                });
    }

    private DriveConnectionResponse toConnectionResponse(GoogleDriveIntegration integration) {
        boolean connected = integration.getStatus() == DriveConnectionStatus.CONNECTED;
        return new DriveConnectionResponse(
                connected,
                integration.getGoogleAccountName(),
                integration.getGoogleAccountEmail(),
                integration.getStatus(),
                integration.getConnectedAt(),
                integration.getLastSyncAt());
    }

    private DriveFileResponse toFileResponse(DriveFile file) {
        return new DriveFileResponse(
                file.id(),
                file.name(),
                file.mimeType(),
                file.webViewLink(),
                file.webContentLink(),
                file.thumbnailLink(),
                parseLong(file.size()),
                file.createdTime());
    }

    private DriveQuota quota(StorageQuota storageQuota) {
        Long limit = storageQuota == null ? null : parseLong(storageQuota.limit());
        Long usage = storageQuota == null ? null : parseLong(storageQuota.usage());
        boolean full = limit != null && limit > 0 && usage != null && usage >= limit;
        return new DriveQuota(limit, usage, full);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant expiresAt(TokenResponse token) {
        return token.expiresIn() == null ? null : Instant.now().plusSeconds(token.expiresIn());
    }

    private void validateRedirectUri(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid Google Drive redirect URI.");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BusinessException("Invalid Google Drive redirect URI.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("Invalid Google Drive redirect URI.");
        }
    }

    private CurrentUser currentUser() {
        return currentUserProvider.currentUser();
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessException("Could not read the media file for Google Drive upload.");
        }
    }
}
