package com.socialhub.socialhubBackend.integration.core.repository;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
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
    default List<SocialIntegration> findByOrganizationIdAndUserId(Long organizationId, Long userId) {
        return findByOrganizationIdAndUserIdAndDeletedAtIsNull(organizationId, userId);
    }

    List<SocialIntegration> findByOrganizationIdAndUserIdAndDeletedAtIsNull(Long organizationId, Long userId);

    /** Ownership-checked by-id lookup: returns the row only if it belongs to this user. */
    default Optional<SocialIntegration> findByIdAndOrganizationIdAndUserId(
            Long id, Long organizationId, Long userId) {
        return findByIdAndOrganizationIdAndUserIdAndDeletedAtIsNull(id, organizationId, userId);
    }

    Optional<SocialIntegration> findByIdAndOrganizationIdAndUserIdAndDeletedAtIsNull(
            Long id, Long organizationId, Long userId);

    default boolean existsByOrganizationIdAndUserIdAndPlatformAndExternalAccountId(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId) {
        return existsByOrganizationIdAndUserIdAndPlatformAndExternalAccountIdAndDeletedAtIsNull(
                organizationId, userId, platform, externalAccountId);
    }

    boolean existsByOrganizationIdAndUserIdAndPlatformAndExternalAccountIdAndDeletedAtIsNull(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId);

    /** Exact identity lookup for an owned platform account/page. */
    default Optional<SocialIntegration> findByOrganizationIdAndUserIdAndPlatformAndExternalAccountId(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId) {
        return findByOrganizationIdAndUserIdAndPlatformAndExternalAccountIdAndDeletedAtIsNull(
                organizationId, userId, platform, externalAccountId);
    }

    Optional<SocialIntegration> findByOrganizationIdAndUserIdAndPlatformAndExternalAccountIdAndDeletedAtIsNull(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId);

    Optional<SocialIntegration> findByOrganizationIdAndUserIdAndPlatformAndExternalAccountIdAndDeletedAtIsNotNull(
            Long organizationId, Long userId, SocialPlatform platform, String externalAccountId);

    default boolean existsByOrganizationIdAndUserIdAndPlatformAndAppCredentialIdAndStatus(
            Long organizationId,
            Long userId,
            SocialPlatform platform,
            Long appCredentialId,
            IntegrationStatus status) {
        return existsByOrganizationIdAndUserIdAndPlatformAndAppCredentialIdAndStatusAndDeletedAtIsNull(
                organizationId, userId, platform, appCredentialId, status);
    }

    boolean existsByOrganizationIdAndUserIdAndPlatformAndAppCredentialIdAndStatusAndDeletedAtIsNull(
            Long organizationId,
            Long userId,
            SocialPlatform platform,
            Long appCredentialId,
            IntegrationStatus status);

    /** Org-wide lookup (no user scope) — only for internal sync jobs, never request-scoped. */
    default List<SocialIntegration> findByOrganizationId(Long organizationId) {
        return findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    List<SocialIntegration> findByOrganizationIdAndDeletedAtIsNull(Long organizationId);
}
