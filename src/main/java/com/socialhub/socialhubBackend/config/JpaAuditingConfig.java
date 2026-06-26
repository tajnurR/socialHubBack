package com.socialhub.socialhubBackend.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Enables JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}/
 * {@code @CreatedBy} on {@code BaseEntity} are populated automatically.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Resolves {@code @CreatedBy} from the authenticated JWT (the user's email),
     * falling back to {@code system} for unauthenticated/startup operations
     * (e.g. the admin seeder). Reads the security context directly so it never
     * fails when there is no current user.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String email = jwtAuth.getToken().getClaimAsString("email");
                return Optional.of(email != null ? email : jwtAuth.getName());
            }
            return Optional.of("system");
        };
    }
}
