package com.socialhub.socialhubBackend.integration.facebook.dto;

import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import java.time.Instant;
import java.util.List;

/** Response DTOs for the Facebook analytics dashboard. */
public final class FacebookAnalyticsDtos {

    private FacebookAnalyticsDtos() {}

    /** A connected Facebook Page (for the pages list). */
    public record ConnectedPage(
            Long integrationId, String pageId, String name, IntegrationStatus status) {}

    /** Page header info. Profile fields are best-effort (may be null if unavailable). */
    public record PageInfo(
            String pageId,
            String name,
            String category,
            String pictureUrl,
            Long fanCount,
            boolean tokenHealthy) {}

    /** At-a-glance KPIs over the analyzed window. Engagement = reactions + comments + shares. */
    public record KpiSummary(
            long totalPosts,
            long totalLikes,
            long totalComments,
            long totalShares,
            long totalReactions,
            long totalEngagement,
            double avgEngagementPerPost,
            BestPost bestPost) {}

    public record BestPost(String id, String message, long engagement, String permalinkUrl) {}

    /** % change vs the previous equal-length window (null when no comparison is possible). */
    public record PeriodComparison(
            Double posts,
            Double likes,
            Double comments,
            Double shares,
            Double reactions,
            Double engagement) {}

    /** One time bucket (day or week) of aggregated engagement. */
    public record TimeSeriesPoint(
            String date,
            long posts,
            long likes,
            long comments,
            long shares,
            long reactions,
            long engagement) {}

    /** A post row for the detailed table. */
    public record PostRow(
            String id,
            String message,
            String fullPicture,
            String permalinkUrl,
            Instant createdTime,
            long likes,
            long comments,
            long shares,
            long reactions,
            long engagement) {}

    /**
     * Full dashboard payload. {@code capped} = the page has more posts than the
     * analyzed window; {@code comparison} is null when no previous period applies.
     */
    public record AnalyticsDashboard(
            PageInfo page,
            KpiSummary summary,
            PeriodComparison comparison,
            List<TimeSeriesPoint> series,
            List<PostRow> posts,
            boolean capped) {}
}
