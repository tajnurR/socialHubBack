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
            SocialPlatform platform,
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
            Instant createdAt) {}

    /** Editable fields of a draft. */
    public record UpdatePostRequest(
            String content, String link, String mediaUrl, Long productId, Long socialIntegrationId) {}

    /** Outcome of a bulk upload: how many imported + per-row errors. */
    public record BulkUploadResult(int importedCount, List<RowError> errors) {}

    public record RowError(int row, String message) {}
}
