package com.socialhub.socialhubBackend.integration.instagram;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.integration.instagram")
public record InstagramProperties(
        String authBaseUrl,
        String tokenUrl,
        String graphBaseUrl,
        String apiVersion,
        List<String> scopes) {

    public InstagramProperties {
        if (authBaseUrl == null || authBaseUrl.isBlank()) {
            authBaseUrl = "https://www.instagram.com/oauth/authorize";
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://api.instagram.com/oauth/access_token";
        }
        if (graphBaseUrl == null || graphBaseUrl.isBlank()) {
            graphBaseUrl = "https://graph.instagram.com";
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "v25.0";
        }
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of("instagram_business_basic", "instagram_business_content_publish");
        }
    }

    public String joinedScopes() {
        return String.join(",", scopes);
    }
}
