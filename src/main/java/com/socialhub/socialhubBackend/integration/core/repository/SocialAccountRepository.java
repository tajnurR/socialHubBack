package com.socialhub.socialhubBackend.integration.core.repository;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    List<SocialAccount> findByOrganizationId(Long organizationId);

    List<SocialAccount> findByOrganizationIdAndPlatform(Long organizationId, SocialPlatform platform);
}
