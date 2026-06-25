package com.socialhub.socialhubBackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security scaffolding.
 *
 * <p><b>Current state:</b> the API is stateless and permits all requests so
 * feature development isn't blocked. Authentication is intentionally NOT
 * implemented yet.
 *
 * <p><b>TODO[SSO]:</b> when an OAuth2/OIDC provider is chosen, plug it in here:
 * <ul>
 *   <li>add {@code spring-boot-starter-oauth2-resource-server}</li>
 *   <li>configure {@code http.oauth2ResourceServer(o -> o.jwt(...))} with the issuer URI</li>
 *   <li>replace {@code .anyRequest().permitAll()} with real authorization rules
 *       (gate {@code /api/**}, keep {@code /actuator/health} and docs open)</li>
 *   <li>add a JWT-to-authorities + tenant converter that populates the tenant context</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints that must stay public regardless of future auth rules. */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    private final AppProperties appProperties;

    public SecurityConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless REST API: no server-side sessions, no CSRF tokens.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // TODO[SSO]: tighten to `.requestMatchers("/api/**").authenticated()`
                        // once the resource server is configured. Permit-all for now.
                        .anyRequest().permitAll());

        // TODO[SSO]: http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.Cors cors = appProperties.cors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setAllowCredentials(cors.allowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
