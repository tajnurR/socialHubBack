package com.socialhub.socialhubBackend.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}/
 * {@code @CreatedBy} on {@code BaseEntity} are populated automatically.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Resolves the "current user" for {@code @CreatedBy}.
     *
     * <p>TODO[SSO]: return the authenticated principal from the SecurityContext
     * once SSO is wired, e.g.
     * {@code SecurityContextHolder.getContext().getAuthentication().getName()}.
     * Until then everything is attributed to {@code system}.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of("system");
    }
}
