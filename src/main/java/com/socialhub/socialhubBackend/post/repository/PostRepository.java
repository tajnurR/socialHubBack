package com.socialhub.socialhubBackend.post.repository;

import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /** Ownership-checked by-id lookup. */
    Optional<Post> findByIdAndOrganizationIdAndUserId(Long id, Long organizationId, Long userId);

    /** Posts belonging to a schedule event (user-scoped). */
    List<Post> findByOrganizationIdAndUserIdAndScheduleEventIdOrderByScheduledAtAsc(
            Long organizationId, Long userId, Long scheduleEventId);

    List<Post> findByOrganizationIdAndUserIdAndScheduleEventIdOrderBySortOrderAscScheduledAtAsc(
            Long organizationId, Long userId, Long scheduleEventId);

    /** User's posts, filtered by optional status/page/product. */
    @Query("""
            select p from Post p
            where p.organizationId = :organizationId and p.userId = :userId
              and (:status is null or p.status = :status)
              and (:pageId is null or p.socialIntegrationId = :pageId)
              and (:productId is null or p.productId = :productId)
            order by p.createdAt desc
            """)
    List<Post> search(
            @Param("organizationId") Long organizationId,
            @Param("userId") Long userId,
            @Param("status") PostStatus status,
            @Param("pageId") Long pageId,
            @Param("productId") Long productId);

    /**
     * Claims a batch of due scheduled posts for publishing. {@code PESSIMISTIC_WRITE}
     * + {@code SKIP_LOCKED} (lock timeout -2) so concurrent instances each grab a
     * disjoint set — multi-instance safe without an external lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select p from Post p
            where p.status = com.socialhub.socialhubBackend.post.domain.PostStatus.SCHEDULED
              and p.scheduledAt <= :now
            order by p.scheduledAt asc
            """)
    List<Post> findDueForUpdate(@Param("now") Instant now, Pageable pageable);
}
