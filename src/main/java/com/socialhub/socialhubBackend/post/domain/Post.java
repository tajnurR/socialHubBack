package com.socialhub.socialhubBackend.post.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A post the user creates/imports, then publishes (now or on a schedule).
 *
 * <p>Owned per user ({@code userId}); targets one of the user's connected pages
 * ({@code socialIntegrationId}). {@code externalPostId} holds the platform's id
 * once published. Scheduling fields ({@code scheduledAt}, {@code scheduleEventId})
 * are set when the post is added to a schedule event.
 */
@Getter
@Setter
@Entity
@Table(name = "posts")
public class Post extends TenantBaseEntity {

    /** Owning user (within the organization). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Target connected page (a SocialIntegration owned by the same user). */
    @Column(name = "social_integration_id")
    private Long socialIntegrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SocialPlatform platform;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 255)
    private String title;

    @Column(length = 2000)
    private String link;

    @Column(name = "media_url", length = 2000)
    private String mediaUrl;

    @Column(length = 500)
    private String hashtags;

    @Column(length = 255)
    private String cta;

    /** Optional product this post is about. */
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status = PostStatus.DRAFT;

    /** When the post should publish (set when scheduled). */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "time_override")
    private java.time.LocalTime timeOverride;

    /** The schedule event this post belongs to, if any. */
    @Column(name = "schedule_event_id")
    private Long scheduleEventId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Platform post id after a successful publish. */
    @Column(name = "external_post_id", length = 255)
    private String externalPostId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;
}
