package com.socialhub.socialhubBackend.integration.core.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
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
import com.socialhub.socialhubBackend.integration.core.mapper.IntegrationMapper;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.tenant.service.OrganizationContextService;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates social media integrations for the current organization:
 * connecting (with live credential validation), listing, disconnecting, and
 * reading/creating posts. Tokens are encrypted on the way in and decrypted only
 * to call the provider.
 */
@Service
@Transactional(readOnly = true)
public class IntegrationService {

    private final SocialMediaProviderRegistry registry;
    private final SocialIntegrationRepository repository;
    private final IntegrationMapper mapper;
    private final EncryptionService encryptionService;
    private final OrganizationContextService organizationContext;

    public IntegrationService(
            SocialMediaProviderRegistry registry,
            SocialIntegrationRepository repository,
            IntegrationMapper mapper,
            EncryptionService encryptionService,
            OrganizationContextService organizationContext) {
        this.registry = registry;
        this.repository = repository;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.organizationContext = organizationContext;
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

    @Transactional
    public IntegrationResponse connect(ConnectIntegrationRequest request) {
        Long organizationId = organizationContext.currentOrganizationId();
        SocialMediaProvider provider = registry.get(request.platform());
        if (!provider.isEnabled()) {
            throw new BusinessException(
                    request.platform() + " integration is not available yet", HttpStatus.BAD_REQUEST);
        }

        // Validates by calling the platform; throws on bad credentials.
        ProviderAccount account = provider.validateCredentials(request.credentials());

        if (repository.existsByOrganizationIdAndPlatformAndExternalAccountId(
                organizationId, request.platform(), account.externalAccountId())) {
            throw new BusinessException("This account is already connected", HttpStatus.CONFLICT);
        }

        SocialIntegration integration = new SocialIntegration();
        integration.setOrganizationId(organizationId);
        integration.setPlatform(request.platform());
        integration.setExternalAccountId(account.externalAccountId());
        integration.setDisplayName(account.displayName());
        integration.setAccessToken(encryptionService.encrypt(account.accessToken()));
        integration.setStatus(IntegrationStatus.CONNECTED);

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

        ProviderPostPage page =
                provider.getPosts(integration.getExternalAccountId(), token, cursor, limit);
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

        var ref = provider.createPost(
                integration.getExternalAccountId(),
                token,
                new CreatePostCommand(request.message(), request.link()));
        return new CreatePostResponse(ref.externalPostId());
    }

    /** Fetches an integration scoped to the current organization, or 404. */
    private SocialIntegration getOwnedIntegration(Long id) {
        Long organizationId = organizationContext.currentOrganizationId();
        return repository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration", id));
    }
}
