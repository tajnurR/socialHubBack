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
 * Unified post model: a single representation of content across every platform.
 * {@code socialAccountId} links it to the connected account it was/will be
 * published through, and {@code externalPostId} stores the platform's own id
 * once published.
 */
@Getter
@Setter
@Entity
@Table(name = "posts")
public class Post extends TenantBaseEntity {

    @Column(name = "social_integration_id")
    private Long socialIntegrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SocialPlatform platform;

    @Column(name = "external_post_id", length = 255)
    private String externalPostId;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;
}
