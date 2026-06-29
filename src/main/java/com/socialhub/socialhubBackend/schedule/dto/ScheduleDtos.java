package com.socialhub.socialhubBackend.schedule.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.post.domain.PostStatus;
import com.socialhub.socialhubBackend.post.dto.PostDtos.PostResponse;
import com.socialhub.socialhubBackend.schedule.domain.ScheduleMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Request/response DTOs for schedule events and rich schedules. */
public final class ScheduleDtos {

    private ScheduleDtos() {}

    /** Legacy schedule-event request. INTERVAL requires {@code startTime} + {@code intervalHours}. */
    public record CreateScheduleEventRequest(
            @NotBlank String name,
            @NotNull ScheduleMode mode,
            Instant startTime,
            Integer intervalHours) {}

    /** One post to attach. {@code scheduledAt} required for EXPLICIT, ignored for INTERVAL. */
    public record ScheduledPostInput(@NotNull Long postId, Instant scheduledAt) {}

    public record AttachPostsRequest(@NotEmpty List<ScheduledPostInput> items) {}

    public record ScheduleEventResponse(
            Long id,
            String name,
            ScheduleMode mode,
            Instant startTime,
            Integer intervalHours,
            String status,
            Instant createdAt,
            List<PostResponse> posts) {}

    public record ScheduleNotifications(
            boolean publishSuccess,
            boolean failure,
            boolean nextPostReminder) {}

    public record SchedulePostRequest(
            Long id,
            String title,
            String caption,
            @NotNull SocialPlatform platform,
            Instant scheduledAt,
            PostStatus status,
            Long socialIntegrationId,
            String mediaUrl,
            String link,
            List<String> hashtags,
            String cta,
            LocalTime timeOverride,
            Integer sortOrder) {}

    public record ScheduleRequest(
            @NotBlank String name,
            String description,
            String color,
            @NotEmpty List<SocialPlatform> platforms,
            @NotBlank String status,
            @NotBlank String scheduleType,
            List<String> daysOfWeek,
            @NotNull LocalTime postingTime,
            @NotBlank String timezone,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Integer dailyPostLimit,
            ScheduleNotifications notifications,
            @Valid List<SchedulePostRequest> posts) {}

    public record SchedulePostResponse(
            Long id,
            String title,
            String caption,
            SocialPlatform platform,
            Instant scheduledAt,
            PostStatus status,
            Long socialIntegrationId,
            String mediaUrl,
            String link,
            List<String> hashtags,
            String cta,
            LocalTime timeOverride,
            Instant publishedAt,
            String externalPostId,
            String errorMessage,
            int sortOrder,
            EngagementSummary engagement) {}

    public record EngagementSummary(long likes, long comments, long shares, long views) {}

    public record ScheduleResponse(
            Long id,
            String name,
            String description,
            String color,
            List<SocialPlatform> platforms,
            String status,
            String scheduleType,
            List<String> daysOfWeek,
            LocalTime postingTime,
            String timezone,
            LocalDate startDate,
            LocalDate endDate,
            Integer dailyPostLimit,
            ScheduleNotifications notifications,
            List<Long> linkedPostIds,
            int totalPosts,
            int postedCount,
            int pendingCount,
            int failedCount,
            Instant nextPostAt,
            int completion,
            int healthScore,
            List<String> healthSuggestions,
            List<BestTimeSuggestion> bestTimes,
            List<ConflictWarning> conflicts,
            List<ScheduleInsight> insights,
            Instant createdAt,
            Instant updatedAt,
            List<SchedulePostResponse> posts) {}

    public record BestTimeSuggestion(SocialPlatform platform, String window, String confidence, LocalTime time) {}

    public record ConflictWarning(String id, String title, String detail, String severity, List<Long> postIds) {}

    public record ScheduleInsight(String id, String text, String tone) {}

    public record QuickPostActionRequest(@NotBlank String action) {}

    public record ReschedulePostRequest(@NotNull Instant scheduledAt) {}

    public record ScheduleTemplateRequest(
            @NotBlank String name,
            String description,
            String color,
            @NotEmpty List<SocialPlatform> platforms,
            @NotBlank String scheduleType,
            List<String> daysOfWeek,
            @NotNull LocalTime postingTime,
            String timezone,
            Integer dailyPostLimit,
            ScheduleNotifications notifications) {}

    public record ScheduleTemplateResponse(
            Long id,
            String name,
            String description,
            String color,
            List<SocialPlatform> platforms,
            String scheduleType,
            List<String> daysOfWeek,
            LocalTime postingTime,
            String timezone,
            Integer dailyPostLimit,
            ScheduleNotifications notifications,
            Instant createdAt) {}
}

