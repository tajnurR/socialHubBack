package com.socialhub.socialhubBackend.tenant.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the active tenant for each request and stores it in {@link TenantContext}.
 *
 * <p><b>Current state (dev placeholder):</b> reads the tenant from the
 * {@code X-Organization-Id} header so the API is usable before auth exists.
 *
 * <p>TODO[SSO]: once SSO is wired, derive the tenant from the authenticated
 * principal's claims (e.g. an {@code org}/{@code tenant} claim on the JWT)
 * instead of trusting a client-supplied header.
 */
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Organization-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(TENANT_HEADER);
            if (header != null && !header.isBlank()) {
                try {
                    TenantContext.setOrganizationId(Long.valueOf(header.trim()));
                } catch (NumberFormatException ignored) {
                    // Malformed header: leave tenant unset rather than failing the request.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
