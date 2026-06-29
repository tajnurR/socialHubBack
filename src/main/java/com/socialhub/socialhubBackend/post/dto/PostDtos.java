package com.socialhub.socialhubBackend.post.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
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
            SocialPlatform platform,
            Long socialIntegrationId,
            Long scheduleEventId,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long productId,
            PostStatus status,
            Instant scheduledAt) {}

    /** Editable fields of a draft. */
    public record UpdatePostRequest(
            SocialPlatform platform,
            Long socialIntegrationId,
            Long scheduleEventId,
            String title,
            String content,
            String link,
            String mediaUrl,
            Long productId,
            PostStatus status,
            Instant scheduledAt) {}

    /** Outcome of a bulk upload: how many imported + per-row errors. */
    public record BulkUploadResult(int importedCount, List<RowError> errors) {}

    public record RowError(int row, String message) {}
}
