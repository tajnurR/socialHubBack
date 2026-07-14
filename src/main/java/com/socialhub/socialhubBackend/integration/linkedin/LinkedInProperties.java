package com.socialhub.socialhubBackend.integration.linkedin;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.integration.linkedin")
public record LinkedInProperties(
        String authBaseUrl,
        String tokenUrl,
        String apiBaseUrl,
        String apiVersion,
        List<String> scopes) {

    public LinkedInProperties {
        if (authBaseUrl == null || authBaseUrl.isBlank()) {
            authBaseUrl = "https://www.linkedin.com/oauth/v2/authorization";
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";
        }
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.linkedin.com";
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "202606";
        }
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of(
                    "openid",
                    "profile",
                    "email",
                    "w_member_social",
                    "r_organization_admin",
                    "w_organization_social");
        }
    }

    public String joinedScopes() {
        return String.join(" ", scopes);
    }
}
