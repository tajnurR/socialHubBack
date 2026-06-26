package com.socialhub.socialhubBackend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the {@code app.*} configuration tree (see application.yml).
 *
 * @param cors     CORS settings applied by {@code SecurityConfig}
 * @param security high-level security toggles ({@code enabled=false} keeps the API
 *                 open during development until SSO is wired)
 * @param crypto   secret used to encrypt data at rest (e.g. integration tokens)
 * @param tenant   tenancy defaults (dev fallback user/organization until SSO provides them)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Security security, Crypto crypto, Tenant tenant) {

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            boolean allowCredentials) {}

    public record Security(boolean enabled) {}

    public record Crypto(String secret) {}

    public record Tenant(Long defaultOrganizationId, Long defaultUserId) {}
}
