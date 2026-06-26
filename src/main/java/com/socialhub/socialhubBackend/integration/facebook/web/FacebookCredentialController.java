package com.socialhub.socialhubBackend.integration.facebook.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialConfigResponse;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialRequest;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialDtos.CredentialStatus;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manage the current user's Meta app credentials (App ID + App Secret). */
@RestController
@RequestMapping("/api/v1/integrations/facebook/credentials")
@Tag(name = "Integrations", description = "Per-user Facebook app credentials")
public class FacebookCredentialController {

    private final FacebookCredentialService credentialService;

    public FacebookCredentialController(FacebookCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping
    @Operation(summary = "Whether Facebook app credentials are configured for this user")
    public ApiResponse<CredentialStatus> status() {
        return ApiResponse.ok(credentialService.status());
    }

    @GetMapping("/configs")
    @Operation(summary = "List the current user's Facebook app configurations")
    public ApiResponse<List<CredentialConfigResponse>> configs() {
        return ApiResponse.ok(credentialService.listConfigs());
    }

    @PostMapping
    @Operation(summary = "Validate and store the user's primary Facebook app credentials")
    public ApiResponse<CredentialStatus> save(@Valid @RequestBody CredentialRequest request) {
        return ApiResponse.ok(
                credentialService.saveOrUpdate(request.appId(), request.appSecret()),
                "Facebook app credentials saved");
    }

    @PostMapping("/configs")
    @Operation(summary = "Validate and add a Facebook app configuration")
    public ApiResponse<CredentialConfigResponse> createConfig(
            @Valid @RequestBody CredentialConfigRequest request) {
        return ApiResponse.ok(credentialService.createConfig(request), "Facebook app configuration saved");
    }
}
