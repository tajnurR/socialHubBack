package com.socialhub.socialhubBackend.media.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_folders")
public class MediaFolder extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "google_drive_folder_id", nullable = false, length = 255)
    private String googleDriveFolderId;

    @Column(name = "google_drive_url", length = 2000)
    private String googleDriveUrl;
}
