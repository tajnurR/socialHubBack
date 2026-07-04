package com.socialhub.socialhubBackend.storage.drive.web;

import com.socialhub.socialhubBackend.common.response.ApiResponse;
import com.socialhub.socialhubBackend.storage.drive.GoogleDriveService;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.AuthorizationUrlRequest;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveConnectionResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveFileResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.DriveFilesResponse;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.OAuthCallbackRequest;
import com.socialhub.socialhubBackend.storage.drive.dto.GoogleDriveDtos.TestConnectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/storage/google-drive")
@Tag(name = "Storage Integrations", description = "Google Drive OAuth storage integration")
public class GoogleDriveController {

    private final GoogleDriveService service;

    public GoogleDriveController(GoogleDriveService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get the current user's Google Drive connection status")
    public ApiResponse<DriveConnectionResponse> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/oauth/authorization-url")
    @Operation(summary = "Create a Google OAuth authorization URL")
    public ApiResponse<AuthorizationUrlResponse> authorizationUrl(
            @Valid @RequestBody AuthorizationUrlRequest request) {
        return ApiResponse.ok(service.authorizationUrl(request.redirectUri(), request.configId()));
    }

    @PostMapping("/oauth/callback")
    @Operation(summary = "Exchange a Google OAuth authorization code and connect Drive")
    public ApiResponse<DriveConnectionResponse> oauthCallback(
            @Valid @RequestBody OAuthCallbackRequest request) {
        return ApiResponse.ok(service.connect(request.code(), request.state()), "Google Drive connected");
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect Google Drive and remove stored tokens")
    public ApiResponse<DriveConnectionResponse> disconnect() {
        return ApiResponse.ok(service.disconnect(), "Google Drive disconnected");
    }

    @PostMapping("/test")
    @Operation(summary = "Test the Google Drive connection")
    public ApiResponse<TestConnectionResponse> testConnection() {
        return ApiResponse.ok(service.testConnection());
    }

    @GetMapping("/files")
    @Operation(summary = "List Google Drive media files available to the app")
    public ApiResponse<DriveFilesResponse> listFiles() {
        return ApiResponse.ok(service.listFiles());
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file to the connected Google Drive account")
    public ApiResponse<DriveFileResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(file), "Media uploaded to Google Drive");
    }
}
