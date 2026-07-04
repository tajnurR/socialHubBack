package com.socialhub.socialhubBackend.media.dto;

import com.socialhub.socialhubBackend.media.domain.MediaType;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import java.time.Instant;
import java.util.List;

public final class MediaDtos {

    private MediaDtos() {}

    public record MediaItemResponse(
            Long mediaId,
            String fileName,
            String originalFileName,
            MediaType mediaType,
            String contentType,
            String extension,
            Long fileSize,
            String checksumSha256,
            String googleDriveFileId,
            String googleDriveUrl,
            String directDownloadUrl,
            String thumbnailUrl,
            MediaUploadStatus uploadStatus,
            String errorMessage,
            long relatedPostCount,
            Instant createdAt,
            Instant updatedAt) {}

    public record MediaUploadItemResult(
            String fileName,
            boolean duplicate,
            boolean uploaded,
            String errorMessage,
            MediaItemResponse media) {}

    public record MediaBulkUploadResult(
            int totalCount,
            int uploadedCount,
            int failedCount,
            int duplicateCount,
            int progressPercentage,
            List<MediaUploadItemResult> items) {}
}
