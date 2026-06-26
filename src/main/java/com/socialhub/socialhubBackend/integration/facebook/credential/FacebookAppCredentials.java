package com.socialhub.socialhubBackend.integration.facebook.credential;

/**
 * Resolved (decrypted) Meta app credentials used for token exchange. Internal only.
 *
 * @param configId   id of the source {@code FacebookAppCredential} (so connections
 *                   can be linked back to the app config that created them)
 * @param appId      Meta app id
 * @param appSecret  decrypted Meta app secret
 * @param apiVersion per-config Graph version override; null → global default
 */
public record FacebookAppCredentials(Long configId, String appId, String appSecret, String apiVersion) {}
