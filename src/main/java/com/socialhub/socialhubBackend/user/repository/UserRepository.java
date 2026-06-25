package com.socialhub.socialhubBackend.user.repository;

import com.socialhub.socialhubBackend.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByOrganizationId(Long organizationId);

    Optional<User> findByOrganizationIdAndEmail(Long organizationId, String email);

    /** TODO[SSO]: lookup by OIDC subject once SSO is wired. */
    Optional<User> findByExternalSubject(String externalSubject);
}
