package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder for syncing posts from connected integrations into the unified
 * {@code posts} table.
 *
 * <p>TODO: for each connected {@code SocialIntegration}, fetch platform posts
 * through a dedicated sync client and upsert the results as {@code Post} rows.
 * Wire to a scheduler or trigger on demand.
 */
@Service
public class PostSyncService {

    private static final Logger log = LoggerFactory.getLogger(PostSyncService.class);

    private final SocialMediaProviderRegistry providerRegistry;
    private final SocialIntegrationRepository socialIntegrationRepository;

    public PostSyncService(
            SocialMediaProviderRegistry providerRegistry,
            SocialIntegrationRepository socialIntegrationRepository) {
        this.providerRegistry = providerRegistry;
        this.socialIntegrationRepository = socialIntegrationRepository;
    }

    /** Sync all connected integrations for an organization. No-op until implemented. */
    public void syncOrganization(Long organizationId) {
        // TODO: iterate socialIntegrationRepository.findByOrganizationId(organizationId)
        //       and persist normalized platform posts.
        log.info("Post sync requested for organization {} (not implemented yet)", organizationId);
    }
}
