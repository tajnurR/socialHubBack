package com.socialhub.socialhubBackend.storage.drive.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveCredentialService;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.CredentialConfigRequest;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.CredentialConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage/google-drive/credentials")
@Tag(name = "Storage Integrations", description = "Per-user Google Drive OAuth app credentials")
public class GoogleDriveCredentialController {

    private final GoogleDriveCredentialService service;

    public GoogleDriveCredentialController(GoogleDriveCredentialService service) {
        this.service = service;
    }

    @GetMapping("/configs")
    @Operation(summary = "List the current user's Google Drive OAuth app configurations")
    public ApiResponse<List<CredentialConfigResponse>> configs() {
        return ApiResponse.ok(service.listConfigs());
    }

    @PostMapping("/configs")
    @Operation(summary = "Add a Google Drive OAuth app configuration")
    public ApiResponse<CredentialConfigResponse> createConfig(
            @Valid @RequestBody CredentialConfigRequest request) {
        return ApiResponse.ok(service.createConfig(request), "Google Drive app configuration saved");
    }
}
