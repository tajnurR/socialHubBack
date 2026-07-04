package com.socialhub.socialhubBackend.media.repository;

import com.socialhub.socialhubBackend.media.domain.MediaAsset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAssetRepository
        extends JpaRepository<MediaAsset, Long>, JpaSpecificationExecutor<MediaAsset> {

    Optional<MediaAsset> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);

    Optional<MediaAsset> findByOrganizationIdAndUserIdAndChecksumSha256AndFileSize(
            Long organizationId, Long userId, String checksumSha256, Long fileSize);

    Optional<MediaAsset> findByOrganizationIdAndUserIdAndGoogleDriveFileId(
            Long organizationId, Long userId, String googleDriveFileId);
}
