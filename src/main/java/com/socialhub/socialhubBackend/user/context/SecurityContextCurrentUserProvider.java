package com.socialhub.socialhubBackend.user.context;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the current user from the authenticated JWT in the security context.
 *
 * <p>The access token carries the user id (subject) and organization (claim
 * {@code org}), so no DB lookup is needed. This is the active
 * {@link CurrentUserProvider}; business code is unaware of how authentication
 * happened, so swapping in SSO only changes who populates the security context.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Long userId = Long.valueOf(jwt.getSubject());
            Long organizationId = jwt.getClaim("org") instanceof Number org ? org.longValue() : null;
            return new CurrentUser(userId, organizationId);
        }
        throw new IllegalStateException("No authenticated user in the security context");
    }
}
