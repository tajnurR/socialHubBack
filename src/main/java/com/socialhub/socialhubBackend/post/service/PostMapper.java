package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import org.springframework.stereotype.Component;

/** Maps {@link Post} entities to {@link PostResponse}. */
@Component
public class PostMapper {

    public PostResponse toResponse(Post p) {
        return new PostResponse(
                p.getId(),
                p.getSocialIntegrationId(),
                p.getPlatform(),
                p.getContent(),
                p.getLink(),
                p.getMediaUrl(),
                p.getProductId(),
                p.getStatus(),
                p.getScheduledAt(),
                p.getPublishedAt(),
                p.getExternalPostId(),
                p.getErrorMessage(),
                p.getScheduleEventId(),
                p.getCreatedAt());
    }
}
