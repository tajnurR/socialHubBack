package com.socialhub.socialhubBackend.integration.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facebook Graph **infrastructure** settings only (non-secret). App credentials
 * (id/secret/redirect/scopes) are stored per-user in the database — there is no
 * global/shared Meta app credential anymore.
 *
 * @param graphBaseUrl base Graph host (default {@code https://graph.facebook.com})
 * @param apiVersion   global default Graph API version (per-config override wins)
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
}
