package com.socialhub.socialhubBackend.integration.facebook.credential;

/**
 * Resolves the Meta app credentials to use for the OAuth token exchange.
 *
 * <p><b>Decision point — shared app vs per-user app:</b> the active
 * implementation ({@link PerOrgFacebookAppCredentialProvider}) reads each
 * organization's own App ID/Secret. To switch to a single shared server-wide app
 * later, provide an alternative implementation (e.g. reading
 * {@code app.integration.facebook.app-id/app-secret}) and make it the primary
 * bean — no caller changes. Callers depend only on this interface.
 */
public interface FacebookAppCredentialProvider {

    /** Resolve credentials for the current organization; throws if not configured. */
    FacebookAppCredentials resolve();

    /** Whether credentials are available for the current organization. */
    boolean isConfigured();
}
