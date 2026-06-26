package com.socialhub.socialhubBackend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings ({@code app.auth.*}).
 *
 * @param jwtSecret        HMAC signing secret (env-provided; SHA-256-derived to a 256-bit key)
 * @param accessTokenTtl   lifetime of access tokens
 * @param refreshTokenTtl  lifetime of refresh tokens
 * @param seedAdminEnabled seed an initial admin user on startup (disable in prod once created)
 * @param seedAdminEmail   initial admin email
 * @param seedAdminPassword initial admin password (dev only — change/disable in prod)
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean seedAdminEnabled,
        String seedAdminEmail,
        String seedAdminPassword) {

    public AuthProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(30);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(14);
        }
    }
}
