package com.socialhub.socialhubBackend.integration.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Facebook Graph API + OAuth settings (overridable via {@code app.integration.facebook.*}).
 *
 * <p>{@code appId}/{@code appSecret} are required for the OAuth token exchange and
 * must stay server-side. {@code scopes} is the comma-separated permission list
 * requested at login. Graph URL/version have sensible defaults.
 *
 * @param graphBaseUrl base Graph host (default {@code https://graph.facebook.com})
 * @param apiVersion   Graph API version (default {@code v25.0})
 * @param appId        Meta app id (public)
 * @param appSecret    Meta app secret (server-side only)
 * @param redirectUri  OAuth redirect URI (used only if the server-side code flow is adopted)
 * @param scopes       comma-separated permissions requested at login
 */
@ConfigurationProperties("app.integration.facebook")
public record FacebookProperties(
        String graphBaseUrl,
        String apiVersion,
        String appId,
        String appSecret,
        String redirectUri,
        String scopes) {

    public FacebookProperties {
        if (graphBaseUrl == null || graphBaseUrl.isBlank()) {
            graphBaseUrl = "https://graph.facebook.com";
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "v25.0";
        }
        if (scopes == null || scopes.isBlank()) {
            scopes = "pages_show_list,pages_read_engagement,pages_manage_posts,pages_read_user_content";
        }
        appId = appId == null ? "" : appId;
        appSecret = appSecret == null ? "" : appSecret;
        redirectUri = redirectUri == null ? "" : redirectUri;
    }

    /** Versioned base URL, e.g. {@code https://graph.facebook.com/v25.0}. */
    public String baseUrl() {
        return graphBaseUrl + "/" + apiVersion;
    }

    public boolean oauthConfigured() {
        return !appId.isBlank() && !appSecret.isBlank();
    }
}
