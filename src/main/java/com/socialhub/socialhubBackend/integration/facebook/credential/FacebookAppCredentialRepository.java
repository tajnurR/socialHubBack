package com.socialhub.socialhubBackend.integration.facebook.credential;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacebookAppCredentialRepository extends JpaRepository<FacebookAppCredential, Long> {

    Optional<FacebookAppCredential> findByOrganizationId(Long organizationId);
}
