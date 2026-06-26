package com.socialhub.socialhubBackend.auth.token;

import com.socialhub.socialhubBackend.user.domain.User;

/**
 * Issues and refreshes auth tokens. <b>The swappable issuance boundary:</b> swap
 * this implementation (and the matching {@code JwtDecoder}) to move from
 * first-party JWTs to SSO/OIDC-issued tokens — nothing else in the app changes.
 */
public interface TokenService {

    /** Issue an access + refresh token pair for a user. */
    AuthTokens issue(User user);

    /** Validate a refresh token and issue a fresh pair (rotates). */
    AuthTokens refresh(String refreshToken);

    record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {}
}
