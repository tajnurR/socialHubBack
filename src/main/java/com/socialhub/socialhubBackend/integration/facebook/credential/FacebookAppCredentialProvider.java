package com.socialhub.socialhubBackend.integration.facebook.credential;

/**
 * Resolves the Meta app credentials to use for the OAuth token exchange, for the
 * current user (via {@code CurrentUserProvider}).
 *
 * <p><b>Decision point — shared app vs per-user app:</b> the active implementation
 * ({@link DbFacebookAppCredentialProvider}) reads the user's own stored App
 * ID/Secret. To switch to a single shared server-wide app later, provide an
 * alternative implementation (e.g. reading {@code app.integration.facebook.app-id/
 * app-secret}) and make it the primary bean — callers depend only on this interface.
 */
public interface FacebookAppCredentialProvider {

    /** The current user's primary app config; throws if none is configured. */
    FacebookAppCredentials resolve();

    /** A specific app config (tenant-scoped); throws if not found. */
    FacebookAppCredentials resolveById(Long configId);

    /** The Graph version override for a config, or {@code null} if none/global default. */
    String apiVersionForConfig(Long configId);

    /** Whether the current user has any app config. */
    boolean isConfigured();
}
