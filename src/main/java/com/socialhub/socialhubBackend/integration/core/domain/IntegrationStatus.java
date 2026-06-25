package com.socialhub.socialhubBackend.integration.core.domain;

/** Connection state of a {@link SocialIntegration}. */
public enum IntegrationStatus {
    CONNECTED,
    /** The stored token was rejected by the platform (expired/invalidated) — user must reconnect. */
    REAUTH_REQUIRED,
    ERROR,
    DISCONNECTED
}
