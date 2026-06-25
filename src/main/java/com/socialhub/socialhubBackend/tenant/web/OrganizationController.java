package com.socialhub.socialhubBackend.tenant.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.tenant.dto.OrganizationResponse;
import com.socialhub.socialhubBackend.tenant.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only organization endpoints. Reference for the standard layering. */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Tenant/organization management")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "List all organizations")
    public ApiResponse<List<OrganizationResponse>> list() {
        return ApiResponse.ok(organizationService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an organization by id")
    public ApiResponse<OrganizationResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(organizationService.findById(id));
    }
}
