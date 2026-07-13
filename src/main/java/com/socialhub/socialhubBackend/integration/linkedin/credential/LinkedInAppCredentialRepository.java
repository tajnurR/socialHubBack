package com.socialhub.socialhubBackend.integration.linkedin.credential;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LinkedInAppCredentialRepository extends JpaRepository<LinkedInAppCredential, Long> {

    List<LinkedInAppCredential> findByOrganizationIdAndUserIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
            Long organizationId, Long userId, LinkedInAppCredentialStatus status);

    Optional<LinkedInAppCredential> findByIdAndOrganizationIdAndUserIdAndStatusAndDeletedAtIsNull(
            Long id, Long organizationId, Long userId, LinkedInAppCredentialStatus status);
}
