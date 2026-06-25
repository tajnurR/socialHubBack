package com.socialhub.socialhubBackend.integration.core.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ConnectIntegrationRequest;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostRequest;
import com.socialhub.socialhubBackend.integration.core.dto.CreatePostResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationPostPageResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderInfo;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Social media integration endpoints, scoped to the caller's organization.
 *
 * <p>Note: paths sit under {@code /api/v1} (the app-wide API prefix) to match the
 * rest of the API and the frontend's configured base URL.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Connect and operate social platform accounts")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/providers")
    @Operation(summary = "List platforms available to connect (with enabled flag)")
    public ApiResponse<List<ProviderInfo>> listProviders() {
        return ApiResponse.ok(integrationService.listProviders());
    }

    @GetMapping
    @Operation(summary = "List connected integrations for the current organization")
    public ApiResponse<List<IntegrationResponse>> list() {
        return ApiResponse.ok(integrationService.listConnected());
    }

    @PostMapping
    @Operation(summary = "Connect a platform (validates credentials against the platform)")
    public ApiResponse<IntegrationResponse> connect(
            @Valid @RequestBody ConnectIntegrationRequest request) {
        return ApiResponse.ok(integrationService.connect(request), "Integration connected");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Disconnect an integration")
    public ApiResponse<Void> disconnect(@PathVariable Long id) {
        integrationService.disconnect(id);
        return ApiResponse.ok(null, "Integration disconnected");
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "List posts for a connected integration")
    public ApiResponse<IntegrationPostPageResponse> getPosts(
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        return ApiResponse.ok(integrationService.getPosts(id, cursor, limit));
    }

    @PostMapping("/{id}/posts")
    @Operation(summary = "Create a new post on a connected integration")
    public ApiResponse<CreatePostResponse> createPost(
            @PathVariable Long id, @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(integrationService.createPost(id, request), "Post created");
    }
}
