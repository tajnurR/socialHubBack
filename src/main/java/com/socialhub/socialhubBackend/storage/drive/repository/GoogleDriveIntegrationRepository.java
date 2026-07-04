package com.socialhub.socialhubBackend.storage.drive.repository;

import com.socialhub.socialhubBackend.storage.drive.domain.GoogleDriveIntegration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleDriveIntegrationRepository extends JpaRepository<GoogleDriveIntegration, Long> {

    Optional<GoogleDriveIntegration> findByOrganizationIdAndUserId(Long organizationId, Long userId);
}
