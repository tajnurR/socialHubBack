package com.socialhub.socialhubBackend.integration.instagram.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.instagram.InstagramCredentialService;
import com.socialhub.socialhubBackend.integration.instagram.InstagramOAuthService;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.AuthorizationUrlRequest;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.ConnectAccountsRequest;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.ConnectRequest;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.ExchangeRequest;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.OAuthCallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/instagram")
@Tag(name = "Instagram Integrations", description = "Instagram Business/Creator OAuth connection")
public class InstagramOAuthController {

    private final InstagramOAuthService service;
    private final InstagramCredentialService credentialService;

    public InstagramOAuthController(InstagramOAuthService service, InstagramCredentialService credentialService) {
        this.service = service;
        this.credentialService = credentialService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/credentials/configs")
    @Operation(summary = "List the current user's Instagram app configurations")
    public ApiResponse<List<CredentialConfigResponse>> configs() {
        return ApiResponse.ok(credentialService.listConfigs());
    }

    @PostMapping("/credentials/configs")
    @Operation(summary = "Add an Instagram app configuration")
    public ApiResponse<CredentialConfigResponse> createConfig(
            @Valid @RequestBody CredentialConfigRequest request) {
        return ApiResponse.ok(credentialService.createConfig(request), "Instagram app configuration saved");
    }

    @PostMapping("/oauth/authorization-url")
    @Operation(summary = "Create an Instagram Login authorization URL")
    public ApiResponse<AuthorizationUrlResponse> authorizationUrl(
            @Valid @RequestBody AuthorizationUrlRequest request) {
        return ApiResponse.ok(service.authorizationUrl(request.redirectUri(), request.configId()));
    }

    @PostMapping("/oauth/callback")
    @Operation(summary = "Connect Instagram with an Instagram Login authorization code")
    public ApiResponse<IntegrationResponse> callback(@Valid @RequestBody OAuthCallbackRequest request) {
        return ApiResponse.ok(
                service.connectWithAuthorizationCode(request.code(), request.state()),
                "Instagram account connected");
    }

    @PostMapping("/oauth/exchange")
    @Operation(summary = "Exchange a short-lived Meta user token for selectable Instagram accounts")
    public ApiResponse<ExchangeResponse> exchange(@Valid @RequestBody ExchangeRequest request) {
        return ApiResponse.ok(service.exchange(request.shortLivedToken(), request.configId()));
    }

    @PostMapping("/connect")
    @Operation(summary = "Connect one Instagram account from an exchange")
    public ApiResponse<IntegrationResponse> connect(@Valid @RequestBody ConnectRequest request) {
        return ApiResponse.ok(service.connect(request.exchangeId(), request.accountId()), "Instagram account connected");
    }

    @PostMapping("/connect/accounts")
    @Operation(summary = "Connect selected Instagram accounts from an exchange")
    public ApiResponse<List<IntegrationResponse>> connectMany(
            @Valid @RequestBody ConnectAccountsRequest request) {
        return ApiResponse.ok(
                service.connectMany(request.exchangeId(), request.accountIds()),
                "Instagram accounts connected");
    }
}
