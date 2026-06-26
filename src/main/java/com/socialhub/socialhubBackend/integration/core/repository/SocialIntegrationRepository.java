package com.socialhub.socialhubBackend.integration.core.repository;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * All lookups are scoped by (organizationId, userId) so one user can never read
 * or act on another user's connections — isolation enforced at the data layer.
 */
@Repository
public interface SocialIntegrationRepository extends JpaRepository<SocialIntegration, Long> {

    /** Connections owned by a user within an organization. */
    List<SocialIntegration> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    /** Ownership-checked by-id lookup: returns the row only if it belongs to this user. */
    Optional<SocialIntegration> findByIdAndOrganizationIdAndUserId(
            Long id, Long organizationId, Long userId);

    boolean existsByOrganizationIdAndUserIdAndPlatformAndExternalAccountId(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId);

    /** Exact identity lookup for an owned platform account/page. */
    Optional<SocialIntegration> findByOrganizationIdAndUserIdAndPlatformAndExternalAccountId(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId);

    /** Org-wide lookup (no user scope) — only for internal sync jobs, never request-scoped. */
    List<SocialIntegration> findByOrganizationId(Long organizationId);
}
