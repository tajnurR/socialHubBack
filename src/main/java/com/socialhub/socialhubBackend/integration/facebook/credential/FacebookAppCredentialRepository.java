package com.socialhub.socialhubBackend.integration.facebook.credential;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacebookAppCredentialRepository extends JpaRepository<FacebookAppCredential, Long> {

    /** All app configs owned by a user within an organization. */
    List<FacebookAppCredential> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    /** The user's primary/default app config (lowest id) — used by the single-config UI. */
    Optional<FacebookAppCredential> findFirstByOrganizationIdAndUserIdOrderByIdAsc(
            Long organizationId, Long userId);

    /** A specific app config owned by a user (ownership-checked). */
    Optional<FacebookAppCredential> findByIdAndOrganizationIdAndUserId(
            Long id, Long organizationId, Long userId);
}
