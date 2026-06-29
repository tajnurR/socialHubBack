package com.socialhub.socialhubBackend.post.service;

import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.repository.SocialIntegrationRepository;
import com.socialhub.socialhubBackend.post.domain.Post;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleEvent;
import com.socialhub.socialhubBackend.schedule.repository.ScheduleEventRepository;
import org.springframework.stereotype.Component;

/** Maps {@link Post} entities to {@link PostResponse}. */
@Component
public class PostMapper {

    private final SocialIntegrationRepository integrationRepository;
    private final ScheduleEventRepository scheduleEventRepository;

    public PostMapper(
            SocialIntegrationRepository integrationRepository,
            ScheduleEventRepository scheduleEventRepository) {
        this.integrationRepository = integrationRepository;
        this.scheduleEventRepository = scheduleEventRepository;
    }

    public PostResponse toResponse(Post p) {
        return new PostResponse(
                p.getId(),
                p.getSocialIntegrationId(),
                targetAccountName(p),
                p.getPlatform(),
                p.getTitle(),
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
                scheduleName(p),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private String targetAccountName(Post post) {
        if (post.getSocialIntegrationId() == null) {
            return null;
        }
        return integrationRepository
                .findByIdAndOrganizationIdAndUserId(
                        post.getSocialIntegrationId(), post.getOrganizationId(), post.getUserId())
                .map(SocialIntegration::getDisplayName)
                .orElse(null);
    }

    private String scheduleName(Post post) {
        if (post.getScheduleEventId() == null) {
            return null;
        }
        return scheduleEventRepository
                .findByIdAndOrganizationIdAndUserId(
                        post.getScheduleEventId(), post.getOrganizationId(), post.getUserId())
                .map(ScheduleEvent::getName)
                .orElse(null);
    }
}
