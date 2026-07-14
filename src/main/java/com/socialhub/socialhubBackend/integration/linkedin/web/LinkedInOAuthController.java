package com.socialhub.socialhubBackend.integration.linkedin.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInOAuthService;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialDtos.CredentialConfigUpdateRequest;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialService;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.AuthorizationUrlRequest;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.ConnectAccountsRequest;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.OAuthCallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/linkedin")
@Tag(name = "LinkedIn Integrations", description = "LinkedIn personal profile OAuth connection")
public class LinkedInOAuthController {

    private final LinkedInOAuthService oauthService;
    private final LinkedInCredentialService credentialService;

    public LinkedInOAuthController(
            LinkedInOAuthService oauthService,
            LinkedInCredentialService credentialService) {
        this.oauthService = oauthService;
        this.credentialService = credentialService;
    }

    @GetMapping("/credentials/configs")
    @Operation(summary = "List the current user's LinkedIn app configurations")
    public ApiResponse<List<CredentialConfigResponse>> configs() {
        return ApiResponse.ok(credentialService.listConfigs());
    }

    @PostMapping("/credentials/configs")
    @Operation(summary = "Add a LinkedIn app configuration")
    public ApiResponse<CredentialConfigResponse> createConfig(
            @Valid @RequestBody CredentialConfigRequest request) {
        return ApiResponse.ok(credentialService.createConfig(request), "LinkedIn app configuration saved");
    }

    @PutMapping("/credentials/configs/{id}")
    @Operation(summary = "Update a LinkedIn app configuration")
    public ApiResponse<CredentialConfigResponse> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody CredentialConfigUpdateRequest request) {
        return ApiResponse.ok(credentialService.updateConfig(id, request), "LinkedIn app configuration updated");
    }

    @DeleteMapping("/credentials/configs/{id}")
    @Operation(summary = "Soft delete a LinkedIn app configuration")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        credentialService.deleteConfig(id);
        return ApiResponse.ok(null, "LinkedIn app configuration removed");
    }

    @PostMapping("/oauth/authorization-url")
    @Operation(summary = "Create a LinkedIn authorization URL")
    public ApiResponse<AuthorizationUrlResponse> authorizationUrl(
            @Valid @RequestBody AuthorizationUrlRequest request) {
        return ApiResponse.ok(oauthService.authorizationUrl(
                request.redirectUri(), request.configId(), request.connectionType()));
    }

    @PostMapping("/oauth/callback")
    @Operation(summary = "Exchange a LinkedIn authorization code for selectable accounts")
    public ApiResponse<ExchangeResponse> callback(@Valid @RequestBody OAuthCallbackRequest request) {
        return ApiResponse.ok(
                oauthService.connectWithAuthorizationCode(request.code(), request.state()),
                "LinkedIn accounts loaded");
    }

    @PostMapping("/connect/accounts")
    @Operation(summary = "Connect selected LinkedIn personal profile or company pages")
    public ApiResponse<List<IntegrationResponse>> connectAccounts(
            @Valid @RequestBody ConnectAccountsRequest request) {
        return ApiResponse.ok(
                oauthService.connectMany(request.exchangeId(), request.accountIds()),
                "LinkedIn accounts connected");
    }
}
