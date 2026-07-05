package com.socialhub.socialhubBackend.media.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_assets")
public class MediaAsset extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false, length = 20)
    private String extension;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "google_drive_file_id", length = 255)
    private String googleDriveFileId;

    @Column(name = "google_drive_url", length = 2000)
    private String googleDriveUrl;

    @Column(name = "direct_download_url", length = 2000)
    private String directDownloadUrl;

    @Column(name = "thumbnail_url", length = 2000)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 30)
    private MediaUploadStatus uploadStatus = MediaUploadStatus.UPLOADING;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
