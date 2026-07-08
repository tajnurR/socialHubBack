package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import com.socialhub.socialhubBackend.media.repository.MediaAssetRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostMediaItemResponse;
import com.socialhub.socialhubBackend.post.repository.PostMediaAssetRepository;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleEvent;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleEventRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Maps {@link Post} entities to {@link PostResponse}. */
@Component
public class PostMapper {

    private final SocialIntegrationRepository integrationRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;

    public PostMapper(
            SocialIntegrationRepository integrationRepository,
            ScheduleEventRepository scheduleEventRepository,
            MediaAssetRepository mediaAssetRepository,
            PostMediaAssetRepository postMediaAssetRepository) {
        this.integrationRepository = integrationRepository;
        this.scheduleEventRepository = scheduleEventRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
    }

    public PostResponse toResponse(Post p) {
        MediaAsset media = linkedMedia(p);
        List<PostMediaItemResponse> mediaItems = linkedMediaItems(p);
        return new PostResponse(
                p.getId(),
                p.getSocialIntegrationId(),
                targetAccountName(p),
                p.getPlatform(),
                p.getTitle(),
                p.getContent(),
                p.getLink(),
                p.getMediaUrl(),
                p.getMediaAssetId(),
                p.getMediaType(),
                media == null ? null : media.getGoogleDriveFileId(),
                media == null ? null : media.getGoogleDriveUrl(),
                media == null ? null : media.getDirectDownloadUrl(),
                media == null ? null : media.getThumbnailUrl(),
                media == null ? null : media.getUploadStatus(),
                mediaItems,
                p.getProductId(),
                p.getStatus(),
                p.getScheduledAt(),
                p.getPublishedAt(),
                p.getExternalPostId(),
                p.getPublishResponseSummary(),
                p.getErrorMessage(),
                p.getRetryCount(),
                p.getLastRetryAt(),
                p.getScheduleEventId(),
                scheduleName(p),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private List<PostMediaItemResponse> linkedMediaItems(Post post) {
        List<com.socialhub.socialhubBackend.post.domain.PostMediaAsset> links =
                postMediaAssetRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId());
        if (links.isEmpty()) {
            MediaAsset media = linkedMedia(post);
            return media == null ? List.of() : List.of(mediaItem(media, 0));
        }
        Map<Long, MediaAsset> mediaById = mediaAssetRepository
                .findByOrganizationIdAndUserIdAndIdInOrderByCreatedAtDesc(
                        post.getOrganizationId(),
                        post.getUserId(),
                        links.stream().map(com.socialhub.socialhubBackend.post.domain.PostMediaAsset::getMediaAssetId).toList())
                .stream()
                .collect(Collectors.toMap(MediaAsset::getId, item -> item, (a, b) -> a));
        return links.stream()
                .map(link -> {
                    MediaAsset media = mediaById.get(link.getMediaAssetId());
                    return media == null ? null : mediaItem(media, link.getDisplayOrder());
                })
                .filter(item -> item != null)
                .toList();
    }

    private PostMediaItemResponse mediaItem(MediaAsset media, int displayOrder) {
        return new PostMediaItemResponse(
                media.getId(),
                media.getMediaType() == null
                        ? null
                        : switch (media.getMediaType()) {
                            case IMAGE -> com.socialhub.socialhubBackend.post.domain.PostMediaType.IMAGE;
                            case VIDEO -> com.socialhub.socialhubBackend.post.domain.PostMediaType.VIDEO;
                        },
                preferredMediaUrl(media),
                media.getGoogleDriveFileId(),
                media.getGoogleDriveUrl(),
                media.getDirectDownloadUrl(),
                media.getThumbnailUrl(),
                media.getUploadStatus(),
                displayOrder);
    }

    private String preferredMediaUrl(MediaAsset asset) {
        if (asset.getDirectDownloadUrl() != null && !asset.getDirectDownloadUrl().isBlank()) {
            return asset.getDirectDownloadUrl();
        }
        if (asset.getGoogleDriveUrl() != null && !asset.getGoogleDriveUrl().isBlank()) {
            return asset.getGoogleDriveUrl();
        }
        return null;
    }

    private MediaAsset linkedMedia(Post post) {
        if (post.getMediaAssetId() == null) {
            return null;
        }
        return mediaAssetRepository
                .findByIdAndOrganizationIdAndUserId(
                        post.getMediaAssetId(), post.getOrganizationId(), post.getUserId())
                .orElse(null);
    }

    private String targetAccountName(Post post) {
        if (post.getSocialIntegrationId() == null) {
            return null;
        }
        return integrationRepository
                .findByIdAndOrganizationIdAndUserId(
                        post.getSocialIntegrationId(), post.getOrganizationId(), post.getUserId())
                .map(SocialIntegration::getDisplayName)
                .orElse(null);
    }

    private String scheduleName(Post post) {
        if (post.getScheduleEventId() == null) {
            return null;
        }
        return scheduleEventRepository
                .findByIdAndOrganizationIdAndUserId(
                        post.getScheduleEventId(), post.getOrganizationId(), post.getUserId())
                .map(ScheduleEvent::getName)
                .orElse(null);
    }
}
