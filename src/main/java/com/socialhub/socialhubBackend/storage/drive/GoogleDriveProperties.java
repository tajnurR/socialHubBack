package com.socialhub.socialhubBackend.storage.drive;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage.google-drive")
public record GoogleDriveProperties(
        String authBaseUrl,
        String tokenUrl,
        String driveBaseUrl,
        String uploadBaseUrl,
        String userInfoUrl,
        List<String> scopes) {

    public GoogleDriveProperties {
        if (authBaseUrl == null || authBaseUrl.isBlank()) {
            authBaseUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://oauth2.googleapis.com/token";
        }
        if (driveBaseUrl == null || driveBaseUrl.isBlank()) {
            driveBaseUrl = "https://www.googleapis.com/drive/v3";
        }
        if (uploadBaseUrl == null || uploadBaseUrl.isBlank()) {
            uploadBaseUrl = "https://www.googleapis.com/upload/drive/v3";
        }
        if (userInfoUrl == null || userInfoUrl.isBlank()) {
            userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";
        }
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of(
                    "https://www.googleapis.com/auth/drive.file",
                    "https://www.googleapis.com/auth/userinfo.email",
                    "https://www.googleapis.com/auth/userinfo.profile");
        }
    }

    public String joinedScopes() {
        return String.join(" ", scopes);
    }
}
