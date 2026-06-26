package com.socialhub.socialhubBackend.integration.core.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.ConnectIntegrationRequest;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostRequest;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationPostPageResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationPostResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderInfo;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostPage;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.core.mapper.IntegrationMapper;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates social media integrations for the current organization:
 * connecting (manual or OAuth-driven), listing, disconnecting, reauth, and
 * reading/creating posts. Tokens are encrypted on the way in and decrypted only
 * to call the provider.
 *
 * <p>Platform OAuth services (e.g. {@code FacebookOAuthService}) reuse
 * {@link #persistConnection} / {@link #reauth} / {@link #getOwnedIntegration} for
 * storage, keeping this core service free of platform-specific code.
 */
@Service
@Transactional(readOnly = true)
public class IntegrationService {

    private final SocialMediaProviderRegistry registry;
    private final SocialIntegrationRepository repository;
    private final IntegrationMapper mapper;
    private final EncryptionService encryptionService;
    private final CurrentUserProvider currentUserProvider;
    private final IntegrationStatusUpdater statusUpdater;

    public IntegrationService(
            SocialMediaProviderRegistry registry,
            SocialIntegrationRepository repository,
            IntegrationMapper mapper,
            EncryptionService encryptionService,
            CurrentUserProvider currentUserProvider,
            IntegrationStatusUpdater statusUpdater) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.currentUserProvider = currentUserProvider;
        this.statusUpdater = statusUpdater;
    }

    /** Lists which platforms can be connected (Facebook enabled; others "coming soon"). */
    public List<ProviderInfo> listProviders() {
        return registry.all().stream()
                .map(provider -> new ProviderInfo(provider.platform(), provider.isEnabled()))
                .sorted(Comparator.comparing(p -> p.platform().name()))
                .toList();
    }

    public List<IntegrationResponse> listConnected() {
        CurrentUser user = currentUserProvider.currentUser();
        return repository.findByOrganizationIdAndUserId(user.organizationId(), user.userId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Manual connect: validate the supplied credentials via the provider, then persist. */
    @Transactional
    public IntegrationResponse connect(ConnectIntegrationRequest request) {
        SocialMediaProvider provider = registry.get(request.platform());
        if (!provider.isEnabled()) {
            throw new BusinessException(
                    request.platform() + " integration is not available yet", HttpStatus.BAD_REQUEST);
        }
        // Validates by calling the platform; throws on bad credentials.
        ProviderAccount account = provider.validateCredentials(request.credentials());
        return persistConnection(request.platform(), account, "MANUAL", null, null);
    }

    /**
     * Persists a new connection (shared by manual and OAuth flows).
     *
     * @param appCredentialId the app config that created this connection (OAuth), or null (manual)
     */
    @Transactional
    public IntegrationResponse persistConnection(
            SocialPlatform platform,
            ProviderAccount account,
            String tokenType,
            Instant expiresAt,
            Long appCredentialId) {
        CurrentUser user = currentUserProvider.currentUser();
        if (repository.existsByOrganizationIdAndUserIdAndPlatformAndExternalAccountId(
                user.organizationId(), user.userId(), platform, account.externalAccountId())) {
            throw new BusinessException(
                    "This account is already connected. Use reconnect to refresh its token.",
                    HttpStatus.CONFLICT);
        }
        SocialIntegration integration = new SocialIntegration();
        integration.setOrganizationId(user.organizationId());
        integration.setUserId(user.userId());
        integration.setPlatform(platform);
        integration.setExternalAccountId(account.externalAccountId());
        integration.setDisplayName(account.displayName());
        integration.setAccessToken(encryptionService.encrypt(account.accessToken()));
        integration.setStatus(IntegrationStatus.CONNECTED);
        integration.setTokenType(tokenType);
        integration.setTokenObtainedAt(Instant.now());
        integration.setTokenExpiresAt(expiresAt);
        integration.setAppCredentialId(appCredentialId);
        return mapper.toResponse(repository.save(integration));
    }

    /** Replaces the stored token of an existing integration in place (reconnect). */
    @Transactional
    public IntegrationResponse reauth(Long id, String rawToken, String tokenType, Instant expiresAt) {
        SocialIntegration integration = getOwnedIntegration(id);
        integration.setAccessToken(encryptionService.encrypt(rawToken));
        integration.setStatus(IntegrationStatus.CONNECTED);
        integration.setTokenType(tokenType);
        integration.setTokenObtainedAt(Instant.now());
        integration.setTokenExpiresAt(expiresAt);
        return mapper.toResponse(repository.save(integration));
    }

    @Transactional
    public void disconnect(Long id) {
        repository.delete(getOwnedIntegration(id));
    }

    public IntegrationPostPageResponse getPosts(Long id, String cursor, int limit) {
        SocialIntegration integration = getOwnedIntegration(id);
        SocialMediaProvider provider = registry.get(integration.getPlatform());
        String token = encryptionService.decrypt(integration.getAccessToken());

        ProviderPostPage page;
        try {
            page = provider.getPosts(integration.getExternalAccountId(), token, cursor, limit);
        } catch (ProviderAuthException ex) {
            statusUpdater.markReauthRequired(integration.getId());
            throw ex;
        }
        List<IntegrationPostResponse> posts = page.posts().stream()
                .map(p -> new IntegrationPostResponse(
                        p.externalId(), p.message(), p.createdTime(), p.fullPicture(), p.permalinkUrl()))
                .toList();
        return new IntegrationPostPageResponse(posts, page.nextCursor());
    }

    public CreatePostResponse createPost(Long id, CreatePostRequest request) {
        SocialIntegration integration = getOwnedIntegration(id);
        SocialMediaProvider provider = registry.get(integration.getPlatform());
        String token = encryptionService.decrypt(integration.getAccessToken());

        try {
            var ref = provider.createPost(
                    integration.getExternalAccountId(),
                    token,
                    new CreatePostCommand(request.message(), request.link()));
            return new CreatePostResponse(ref.externalPostId());
        } catch (ProviderAuthException ex) {
            statusUpdater.markReauthRequired(integration.getId());
            throw ex;
        }
    }

    /**
     * Fetches an integration owned by the current user, or 404. This is the single
     * ownership gate every by-id operation (posts, analytics, disconnect, reauth)
     * goes through — a resource not owned by the current user is indistinguishable
     * from one that doesn't exist.
     */
    public SocialIntegration getOwnedIntegration(Long id) {
        CurrentUser user = currentUserProvider.currentUser();
        return repository
                .findByIdAndOrganizationIdAndUserId(id, user.organizationId(), user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Integration", id));
    }
}
