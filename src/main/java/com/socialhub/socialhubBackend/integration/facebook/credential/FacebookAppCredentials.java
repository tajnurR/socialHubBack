package com.socialhub.socialhubBackend.integration.facebook.credential;

/** Resolved (decrypted) Meta app credentials used for token exchange. Internal only. */
public record FacebookAppCredentials(String appId, String appSecret) {}
