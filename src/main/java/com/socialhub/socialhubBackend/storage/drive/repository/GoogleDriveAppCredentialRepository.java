package com.socialhub.socialhubBackend.storage.drive.repository;

import com.socialhub.socialhubBackend.storage.drive.domain.GoogleDriveAppCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleDriveAppCredentialRepository extends JpaRepository<GoogleDriveAppCredential, Long> {

    List<GoogleDriveAppCredential> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    Optional<GoogleDriveAppCredential> findFirstByOrganizationIdAndUserIdOrderByIdAsc(
            Long organizationId, Long userId);

    Optional<GoogleDriveAppCredential> findByIdAndOrganizationIdAndUserId(
            Long id, Long organizationId, Long userId);
}
