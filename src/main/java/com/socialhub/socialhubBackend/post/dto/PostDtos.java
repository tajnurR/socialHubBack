package com.socialhub.socialhubBackend.post.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Request/response DTOs for posts and bulk upload. */
public final class PostDtos {

    private PostDtos() {}

    public record PostResponse(
            Long id,
            Long socialIntegrationId,
            String targetAccountName,
            SocialPlatform platform,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long mediaAssetId,
            PostMediaType mediaType,
            String googleDriveFileId,
            String googleDriveUrl,
            String directDownloadUrl,
            String thumbnailUrl,
            MediaUploadStatus mediaUploadStatus,
            Long productId,
            PostStatus status,
            Instant scheduledAt,
            Instant publishedAt,
            String externalPostId,
            String errorMessage,
            Long scheduleEventId,
            String scheduleName,
            Instant createdAt,
            Instant updatedAt) {}

    /** Create a single owned post from the Post Management form. */
    public record CreatePostRequest(
            @NotNull SocialPlatform platform,
            @NotNull Long socialIntegrationId,
            @NotBlank String title,
            @NotBlank String content,
            String link,
            String mediaUrl,
            Long mediaAssetId,
            @NotNull Long productId) {}

    /** Editable fields of a draft. */
    public record UpdatePostRequest(
            SocialPlatform platform,
            Long socialIntegrationId,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long mediaAssetId,
            Long productId) {}

    /** Outcome of a bulk upload: how many imported + per-row errors. */
    public record BulkUploadResult(int importedCount, List<RowError> errors) {}

    public record RowError(int row, String message) {}
}
