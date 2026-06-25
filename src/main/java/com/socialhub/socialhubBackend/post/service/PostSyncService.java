package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.repository.SocialAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder for syncing posts from connected accounts into the unified
 * {@code posts} table.
 *
 * <p>TODO: for each connected {@code SocialAccount}, resolve its provider via
 * {@link SocialMediaProviderRegistry}, call {@code fetchRecentPosts(...)}, and
 * upsert results as {@code Post} rows. Wire to a scheduler (e.g.
 * {@code @Scheduled}) or trigger on demand once providers are implemented.
 */
@Service
public class PostSyncService {

    private static final Logger log = LoggerFactory.getLogger(PostSyncService.class);

    private final SocialMediaProviderRegistry providerRegistry;
    private final SocialAccountRepository socialAccountRepository;

    public PostSyncService(
            SocialMediaProviderRegistry providerRegistry,
            SocialAccountRepository socialAccountRepository) {
        this.providerRegistry = providerRegistry;
        this.socialAccountRepository = socialAccountRepository;
    }

    /** Sync all connected accounts for an organization. No-op until providers exist. */
    public void syncOrganization(Long organizationId) {
        // TODO: iterate socialAccountRepository.findByOrganizationId(organizationId),
        //       fetch posts via providerRegistry.get(account.getPlatform()), and persist.
        log.info("Post sync requested for organization {} (not implemented yet)", organizationId);
    }
}
