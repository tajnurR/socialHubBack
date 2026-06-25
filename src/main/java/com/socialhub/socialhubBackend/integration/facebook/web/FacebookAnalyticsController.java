package com.socialhub.socialhubBackend.integration.facebook.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostRequest;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostResponse;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookAnalyticsService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookAnalyticsService.Granularity;
import com.socialhub.socialhubBackend.integration.facebook.FacebookAnalyticsService.SortBy;
import com.socialhub.socialhubBackend.integration.facebook.FacebookAnalyticsService.SortOrder;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.AnalyticsDashboard;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookAnalyticsDtos.ConnectedPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Facebook Pages list + per-page analytics, plus single-post create (reused). */
@RestController
@RequestMapping("/api/v1/facebook/pages")
@Tag(name = "Facebook", description = "Connected Facebook Pages and analytics")
public class FacebookAnalyticsController {

    private final FacebookAnalyticsService analyticsService;
    private final IntegrationService integrationService;

    public FacebookAnalyticsController(
            FacebookAnalyticsService analyticsService, IntegrationService integrationService) {
        this.analyticsService = analyticsService;
        this.integrationService = integrationService;
    }

    @GetMapping
    @Operation(summary = "List connected Facebook Pages for the current organization")
    public ApiResponse<List<ConnectedPage>> pages() {
        return ApiResponse.ok(analyticsService.connectedPages());
    }

    @GetMapping("/{integrationId}/analytics")
    @Operation(summary = "Dashboard: page info, KPIs, time-series, and filtered/sorted posts")
    public ApiResponse<AnalyticsDashboard> analytics(
            @PathVariable Long integrationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long minLikes,
            @RequestParam(required = false) Long minComments,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String granularity) {
        return ApiResponse.ok(analyticsService.analytics(
                integrationId, from, to, minLikes, minComments,
                parseSortBy(sortBy), parseOrder(order), parseGranularity(granularity)));
    }

    @PostMapping("/{integrationId}/posts")
    @Operation(summary = "Create a single post on a Page (reuses the existing create flow)")
    public ApiResponse<CreatePostResponse> createPost(
            @PathVariable Long integrationId, @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(integrationService.createPost(integrationId, request), "Post created");
    }

    private SortBy parseSortBy(String value) {
        if (value == null || value.isBlank()) {
            return SortBy.DATE;
        }
        try {
            return SortBy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SortBy.DATE;
        }
    }

    private SortOrder parseOrder(String value) {
        if (value == null || value.isBlank()) {
            return SortOrder.DESC;
        }
        try {
            return SortOrder.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SortOrder.DESC;
        }
    }

    private Granularity parseGranularity(String value) {
        if (value == null || value.isBlank()) {
            return Granularity.DAY;
        }
        try {
            return Granularity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Granularity.DAY;
        }
    }
}
