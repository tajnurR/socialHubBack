package com.socialhub.socialhubBackend.media.dto;

import com.socialhub.socialhubBackend.media.domain.MediaType;
import com.socialhub.socialhubBackend.media.domain.MediaUploadStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class MediaDtos {

    private MediaDtos() {}

    public record MediaFolderResponse(
            Long folderId,
            String name,
            String googleDriveFolderId,
            String googleDriveUrl,
            long mediaCount,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateMediaFolderRequest(@NotBlank String name) {}

    public record MediaItemResponse(
            Long mediaId,
            String fileName,
            String originalFileName,
            MediaType mediaType,
            String contentType,
            String extension,
            Long fileSize,
            String checksumSha256,
            Long folderId,
            String folderName,
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

    public record MediaPageResponse(
            List<MediaItemResponse> items,
            long totalCount,
            int page,
            int pageSize,
            int totalPages) {}

    public record MediaBulkUploadResult(
            int totalCount,
            int uploadedCount,
            int failedCount,
            int duplicateCount,
            int progressPercentage,
            List<MediaUploadItemResult> items) {}
}
