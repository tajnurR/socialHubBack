package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.common.security.EncryptionService;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.integration.core.exception.ProviderAuthException;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationStatusUpdater;
import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import com.socialhub.socialhubBackend.media.repository.MediaAssetRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveClient.DownloadedFile;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Publishes a single {@link Post} to its target page. Resolves the page + token
 * from the <b>post's own owner</b> (org + user stored on the post), NOT the
 * request's current user — so the background scheduler can publish on a user's
 * behalf. The caller owns status transitions and persistence.
 */
@Component
public class PostPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostPublisher.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final SocialMediaProviderRegistry registry;
    private final SocialIntegrationRepository integrationRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final GoogleDriveService googleDriveService;
    private final EncryptionService encryptionService;
    private final IntegrationStatusUpdater statusUpdater;

    public PostPublisher(
            SocialMediaProviderRegistry registry,
            SocialIntegrationRepository integrationRepository,
            MediaAssetRepository mediaAssetRepository,
            GoogleDriveService googleDriveService,
            EncryptionService encryptionService,
            IntegrationStatusUpdater statusUpdater) {
        this.registry = registry;
        this.integrationRepository = integrationRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.googleDriveService = googleDriveService;
        this.encryptionService = encryptionService;
        this.statusUpdater = statusUpdater;
    }

    /** Attempts to publish the post and returns the outcome for the caller to persist. */
    public PublishAttempt publish(Post post) {
        if (post.getSocialIntegrationId() == null) {
            return fail("Select a target page/account before publishing.", false);
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            return fail("Post message is required.", false);
        }

        SocialIntegration integration = null;
        try {
            integration = integrationRepository
                    .findByIdAndOrganizationIdAndUserId(
                            post.getSocialIntegrationId(), post.getOrganizationId(), post.getUserId())
                    .orElse(null);
            if (integration == null) {
                return fail("Target page is not connected (or no longer owned by you).", false);
            }

            SocialMediaProvider provider = registry.get(integration.getPlatform());
            String token = encryptionService.decrypt(integration.getAccessToken());
            MediaPayload media = resolveMedia(post, integration.getPlatform());
            ProviderPostRef ref = provider.createPost(
                    integration.getExternalAccountId(),
                    token,
                    new CreatePostCommand(
                            post.getContent(),
                            post.getMediaType() == null ? post.getLink() : null,
                            post.getMediaUrl(),
                            post.getMediaType(),
                            media == null ? null : media.filename(),
                            media == null ? null : media.contentType(),
                            media == null ? null : media.bytes()));
            return PublishAttempt.success(ref.externalPostId(), Instant.now(), truncate(ref.responseSummary()));
        } catch (ProviderAuthException ex) {
            if (integration != null) {
                statusUpdater.markReauthRequired(integration.getId());
            }
            return fail("Reconnect needed: " + ex.getMessage(), false);
        } catch (BusinessException ex) {
            return fail(ex.getMessage(), retryable(ex.getStatus()));
        } catch (RuntimeException ex) {
            log.warn("Publish failed for post {}: {}", post.getId(), ex.getMessage());
            return fail(ex.getMessage(), true);
        }
    }

    private MediaPayload resolveMedia(Post post, SocialPlatform platform) {
        if (post.getMediaType() == null || post.getMediaAssetId() == null) {
            return null;
        }
        MediaAsset media = mediaAssetRepository
                .findByIdAndOrganizationIdAndUserId(post.getMediaAssetId(), post.getOrganizationId(), post.getUserId())
                .orElseThrow(() -> new BusinessException("Selected media is no longer available.", HttpStatus.BAD_REQUEST));
        if (media.getUploadStatus() != MediaUploadStatus.UPLOADED) {
            throw new BusinessException("Media upload is not complete yet.", HttpStatus.BAD_REQUEST);
        }
        if (media.getGoogleDriveFileId() == null || media.getGoogleDriveFileId().isBlank()) {
            throw new BusinessException(
                    "Google Drive file reference is missing for the selected media.",
                    HttpStatus.BAD_REQUEST);
        }
        if (platform == SocialPlatform.INSTAGRAM) {
            return new MediaPayload(
                    media.getOriginalFileName() != null && !media.getOriginalFileName().isBlank()
                            ? media.getOriginalFileName()
                            : media.getFileName(),
                    media.getContentType(),
                    null);
        }
        DownloadedFile downloaded = googleDriveService.downloadMediaFile(
                post.getOrganizationId(), post.getUserId(), media.getGoogleDriveFileId());
        return new MediaPayload(
                media.getOriginalFileName() != null && !media.getOriginalFileName().isBlank()
                        ? media.getOriginalFileName()
                        : media.getFileName(),
                downloaded.contentType() != null && !downloaded.contentType().isBlank()
                        ? downloaded.contentType()
                        : media.getContentType(),
                downloaded.body());
    }

    private PublishAttempt fail(String message, boolean retryable) {
        return PublishAttempt.failure(truncate(message), retryable);
    }

    private boolean retryable(HttpStatus status) {
        return status != null
                && (status.is5xxServerError()
                        || status == HttpStatus.REQUEST_TIMEOUT
                        || status == HttpStatus.TOO_MANY_REQUESTS);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private record MediaPayload(String filename, String contentType, byte[] bytes) {}

    public record PublishAttempt(
            boolean successful,
            boolean retryable,
            String externalPostId,
            Instant publishedAt,
            String responseSummary,
            String failureReason) {

        static PublishAttempt success(String externalPostId, Instant publishedAt, String responseSummary) {
            return new PublishAttempt(true, false, externalPostId, publishedAt, responseSummary, null);
        }

        static PublishAttempt failure(String failureReason, boolean retryable) {
            return new PublishAttempt(false, retryable, null, null, null, failureReason);
        }
    }
}
