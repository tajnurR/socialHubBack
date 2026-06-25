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
import com.socialhub.socialhubBackend.tenant.service.OrganizationContextService;
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
    private final OrganizationContextService organizationContext;
    private final IntegrationStatusUpdater statusUpdater;

    public IntegrationService(
            SocialMediaProviderRegistry registry,
            SocialIntegrationRepository repository,
            IntegrationMapper mapper,
            EncryptionService encryptionService,
            OrganizationContextService organizationContext,
            IntegrationStatusUpdater statusUpdater) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.organizationContext = organizationContext;
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
        Long organizationId = organizationContext.currentOrganizationId();
        return repository.findByOrganizationId(organizationId).stream()
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
        return persistConnection(request.platform(), account, "MANUAL", null);
    }

    /** Persists a new connection (shared by manual and OAuth flows). */
    @Transactional
    public IntegrationResponse persistConnection(
            SocialPlatform platform, ProviderAccount account, String tokenType, Instant expiresAt) {
        Long organizationId = organizationContext.currentOrganizationId();
        if (repository.existsByOrganizationIdAndPlatformAndExternalAccountId(
                organizationId, platform, account.externalAccountId())) {
            throw new BusinessException(
                    "This account is already connected. Use reconnect to refresh its token.",
                    HttpStatus.CONFLICT);
        }
        SocialIntegration integration = new SocialIntegration();
        integration.setOrganizationId(organizationId);
        integration.setPlatform(platform);
        integration.setExternalAccountId(account.externalAccountId());
        integration.setDisplayName(account.displayName());
        integration.setAccessToken(encryptionService.encrypt(account.accessToken()));
        integration.setStatus(IntegrationStatus.CONNECTED);
        integration.setTokenType(tokenType);
        integration.setTokenObtainedAt(Instant.now());
        integration.setTokenExpiresAt(expiresAt);
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

    /** Fetches an integration scoped to the current organization, or 404. */
    public SocialIntegration getOwnedIntegration(Long id) {
        Long organizationId = organizationContext.currentOrganizationId();
        return repository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", id));
    }
}
