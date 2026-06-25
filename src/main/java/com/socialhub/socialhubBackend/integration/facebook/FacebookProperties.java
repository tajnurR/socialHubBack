package com.socialhub.socialhubBackend.integration.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facebook Graph API settings. Overridable via {@code app.integration.facebook.*}
 * (defaults are sensible, so no config is required).
 */
@ConfigurationProperties("app.integration.facebook")
public record FacebookProperties(String graphBaseUrl, String apiVersion) {

    public FacebookProperties {
        if (graphBaseUrl == null || graphBaseUrl.isBlank()) {
            graphBaseUrl = "https://graph.facebook.com";
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "v25.0";
        }
    }

    /** Versioned base URL, e.g. {@code https://graph.facebook.com/v21.0}. */
    public String baseUrl() {
        return graphBaseUrl + "/" + apiVersion;
    }
}
