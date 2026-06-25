package com.socialhub.socialhubBackend.analytics.web;

import com.socialhub.socialhubBackend.analytics.dto.AnalyticsSummary;
import com.socialhub.socialhubBackend.analytics.service.AnalyticsService;
import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics endpoints (structure only). Reads the active tenant from
 * {@link TenantContext} (populated from the {@code X-Organization-Id} header for now).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Cross-platform analytics aggregation")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Aggregated analytics summary for the current organization")
    public ApiResponse<AnalyticsSummary> summary() {
        Long organizationId = TenantContext.getOrganizationId();
        return ApiResponse.ok(analyticsService.summaryForOrganization(organizationId));
    }
}
