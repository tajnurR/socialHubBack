package com.socialhub.socialhubBackend.integration.facebook;

import java.util.Map;

/**
 * Single source of truth for every Meta Graph API path and field set used in the
 * app. All Facebook calls in {@code FacebookGraphClient} route through here, so a
 * Graph change (new path, renamed field, version bump) is edited in one place.
 *
 * <p>Path templates use {@code {name}} placeholders; {@link #path} substitutes
 * them and prefixes the (per-config or global) API version, e.g.
 * {@code /v25.0/{pageId}/feed} → {@code /v25.0/123/feed}.
 */
public enum FacebookGraphApi {

    /** Token exchange + app-token validation. */
    OAUTH_ACCESS_TOKEN("oauth/access_token"),
    /** Pages the (user) token manages, each with a Page token. */
    ME_ACCOUNTS("me/accounts"),
    /** A single page node (validation / profile). */
    PAGE("{pageId}"),
    /** A page's published posts edge. */
    PAGE_PUBLISHED_POSTS("{pageId}/published_posts"),
    /** A page's feed edge (publish). */
    PAGE_FEED("{pageId}/feed");

    private final String template;

    FacebookGraphApi(String template) {
        this.template = template;
    }

    /** Version-prefixed path with no path params (e.g. {@code /v25.0/me/accounts}). */
    public String path(String apiVersion) {
        return path(apiVersion, Map.of());
    }

    /** Version-prefixed path with {@code {name}} params substituted. */
    public String path(String apiVersion, Map<String, String> params) {
        String resolved = template;
        for (Map.Entry<String, String> param : params.entrySet()) {
            resolved = resolved.replace("{" + param.getKey() + "}", param.getValue());
        }
        return "/" + apiVersion + "/" + resolved;
    }

    /** Reusable {@code fields} query fragments. */
    public static final class Fields {

        private Fields() {}

        /** Validate a token + resolve the Page token. */
        public static final String PAGE_VALIDATE = "name,access_token";

        /** {@code /me/accounts} rows: id, name, Page token. */
        public static final String MANAGED_PAGES = "id,name,access_token";

        /** Page header profile for the dashboard. */
        public static final String PAGE_PROFILE = "name,fan_count,picture.type(large),category";

        /** Post fields incl. engagement summaries. */
        public static final String POST =
                "id,message,created_time,full_picture,permalink_url,shares,"
                        + "likes.summary(true),comments.summary(true),reactions.summary(true)";
    }
}
