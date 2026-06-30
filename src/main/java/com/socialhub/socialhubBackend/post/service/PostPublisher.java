package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationStatusUpdater;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes a single {@link Post} to its target page. Resolves the page + token
 * from the <b>post's own owner</b> (org + user stored on the post), NOT the
 * request's current user — so the background scheduler can publish on a user's
 * behalf. Mutates the post's status/result; the caller persists it.
 */
@Component
public class PostPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostPublisher.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final SocialMediaProviderRegistry registry;
    private final SocialIntegrationRepository integrationRepository;
    private final EncryptionService encryptionService;
    private final IntegrationStatusUpdater statusUpdater;

    public PostPublisher(
            SocialMediaProviderRegistry registry,
            SocialIntegrationRepository integrationRepository,
            EncryptionService encryptionService,
            IntegrationStatusUpdater statusUpdater) {
        this.registry = registry;
        this.integrationRepository = integrationRepository;
        this.encryptionService = encryptionService;
        this.statusUpdater = statusUpdater;
    }

    /** Attempts to publish the post; sets POSTED (+ external id) or FAILED (+ error). */
    public void publish(Post post) {
        if (post.getSocialIntegrationId() == null) {
            fail(post, "Select a target page/account before publishing.");
            return;
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            fail(post, "Post message is required.");
            return;
        }
        SocialIntegration integration = null;
        try {
            integration = integrationRepository
                    .findByIdAndOrganizationIdAndUserId(
                            post.getSocialIntegrationId(), post.getOrganizationId(), post.getUserId())
                    .orElse(null);
            if (integration == null) {
                fail(post, "Target page is not connected (or no longer owned by you).");
                return;
            }
            SocialMediaProvider provider = registry.get(integration.getPlatform());
            String token = encryptionService.decrypt(integration.getAccessToken());
            ProviderPostRef ref = provider.createPost(
                    integration.getExternalAccountId(),
                    token,
                    new CreatePostCommand(post.getContent(), post.getLink()));
            post.setExternalPostId(ref.externalPostId());
            post.setStatus(PostStatus.POSTED);
            post.setPublishedAt(Instant.now());
            post.setErrorMessage(null);
        } catch (ProviderAuthException ex) {
            if (integration != null) {
                statusUpdater.markReauthRequired(integration.getId());
            }
            fail(post, "Reconnect needed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Publish failed for post {}: {}", post.getId(), ex.getMessage());
            fail(post, ex.getMessage());
        }
    }

    private void fail(Post post, String message) {
        post.setStatus(PostStatus.FAILED);
        post.setErrorMessage(truncate(message));
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
