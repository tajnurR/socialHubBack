package com.socialhub.socialhubBackend.post.repository;

import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** Ownership-checked by-id lookup. */
    Optional<Post> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Post p
            where p.id = :id
              and p.organizationId = :organizationId
              and p.userId = :userId
            """)
    Optional<Post> findByIdAndOrganizationIdAndUserIdForUpdate(
            @Param("id") Long id,
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    /** Posts belonging to a schedule event (user-scoped). */
    List<Post> findByOrganizationIdAndUserIdAndScheduleEventIdOrderByScheduledAtAsc(
            Long organizationId, Long userId, Long scheduleEventId);

    List<Post> findByOrganizationIdAndUserIdAndScheduleEventIdOrderBySortOrderAscScheduledAtAsc(
            Long organizationId, Long userId, Long scheduleEventId);

    @Query("""
            select count(distinct p.id) from Post p
            where p.organizationId = :organizationId
              and p.userId = :userId
              and (p.mediaAssetId = :mediaAssetId or p.mediaUrl in :mediaUrls)
            """)
    long countRelatedToMedia(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("mediaAssetId") Long mediaAssetId,
            @Param("mediaUrls") Collection<String> mediaUrls);

    /**
     * Claims a batch of due scheduled posts for publishing. {@code PESSIMISTIC_WRITE}
     * + {@code SKIP_LOCKED} (lock timeout -2) so concurrent instances each grab a
     * disjoint set — multi-instance safe without an external lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select p from Post p
            where p.status = com.socialhub.socialhubBackend.post.domain.PostStatus.PENDING
              and p.scheduledAt <= :now
            order by p.scheduledAt asc
            """)
    List<Post> findDueForUpdate(@Param("now") Instant now, Pageable pageable);
}
