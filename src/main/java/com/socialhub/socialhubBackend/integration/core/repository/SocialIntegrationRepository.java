package com.socialhub.socialhubBackend.integration.core.repository;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialIntegrationRepository extends JpaRepository<SocialIntegration, Long> {

    List<SocialIntegration> findByOrganizationId(Long organizationId);

    /** Tenant-scoped lookup: only returns the row if it belongs to the organization. */
    Optional<SocialIntegration> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndPlatformAndExternalAccountId(
            Long organizationId, SocialPlatform platform, String externalAccountId);
}
