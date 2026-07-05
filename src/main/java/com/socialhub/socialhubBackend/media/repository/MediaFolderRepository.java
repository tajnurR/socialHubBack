package com.socialhub.socialhubBackend.media.repository;

import com.socialhub.socialhubBackend.media.domain.MediaFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {

    List<MediaFolder> findByOrganizationIdAndUserIdOrderByNameAsc(Long organizationId, Long userId);

    Optional<MediaFolder> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);

    Optional<MediaFolder> findByOrganizationIdAndUserIdAndNameIgnoreCase(Long organizationId, Long userId, String name);
}
