package com.socialhub.socialhubBackend.integration.core.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderInfo;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the social platform integrations the backend knows about. Useful as a
 * smoke test that the plug-in registry wired up every provider at startup.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Registered social platform providers")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/providers")
    @Operation(summary = "List all registered social media providers")
    public ApiResponse<List<ProviderInfo>> listProviders() {
        return ApiResponse.ok(integrationService.listProviders());
    }
}
