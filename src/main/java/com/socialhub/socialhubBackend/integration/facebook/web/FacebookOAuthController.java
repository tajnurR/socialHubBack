package com.socialhub.socialhubBackend.integration.facebook.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.facebook.FacebookOAuthService;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.ConnectRequest;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.ExchangeRequest;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.ReauthRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facebook OAuth (Login) endpoints, organization-scoped. The frontend obtains a
 * short-lived user token via the FB JS SDK and posts it here; the backend does
 * the long-lived exchange + page-token resolution.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Facebook OAuth connect flow")
public class FacebookOAuthController {

    private final FacebookOAuthService oauthService;

    public FacebookOAuthController(FacebookOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @PostMapping("/facebook/oauth/exchange")
    @Operation(summary = "Exchange a short-lived FB user token and return selectable pages")
    public ApiResponse<ExchangeResponse> exchange(@Valid @RequestBody ExchangeRequest request) {
        return ApiResponse.ok(oauthService.exchange(request.shortLivedToken()));
    }

    @PostMapping("/facebook/connect")
    @Operation(summary = "Connect a chosen Facebook Page from a prior exchange")
    public ApiResponse<IntegrationResponse> connect(@Valid @RequestBody ConnectRequest request) {
        return ApiResponse.ok(
                oauthService.connect(request.exchangeId(), request.pageId()), "Integration connected");
    }

    @PostMapping("/{id}/reauth")
    @Operation(summary = "Re-authenticate an existing integration (replace its token in place)")
    public ApiResponse<IntegrationResponse> reauth(
            @PathVariable Long id, @Valid @RequestBody ReauthRequest request) {
        return ApiResponse.ok(
                oauthService.reauth(id, request.exchangeId()), "Integration reconnected");
    }
}
