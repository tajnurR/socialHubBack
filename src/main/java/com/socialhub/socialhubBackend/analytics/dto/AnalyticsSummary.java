package com.socialhub.socialhubBackend.analytics.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import java.util.Map;

/**
 * Aggregated analytics across an organization's accounts and posts.
 *
 * @param totalAccounts     number of connected accounts
 * @param totalPosts        number of posts tracked
 * @param totalImpressions  summed impressions
 * @param totalEngagements  summed likes + comments + shares
 * @param postsByPlatform   post counts broken down per platform
 */
public record AnalyticsSummary(
        long totalAccounts,
        long totalPosts,
        long totalImpressions,
        long totalEngagements,
        Map<SocialPlatform, Long> postsByPlatform) {

    /** An empty summary; the shape analytics endpoints return before data exists. */
    public static AnalyticsSummary empty() {
        return new AnalyticsSummary(0, 0, 0, 0, Map.of());
    }
}
