/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class SharedSecretAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Tenant-Runtime-Secret";

    private final TaalamTenantRuntimeProperties properties;

    public SharedSecretAuthFilter(TaalamTenantRuntimeProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String expectedSecret = properties.getSharedSecret();
        if (!StringUtils.hasText(expectedSecret)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Tenant runtime shared secret is not configured");
            return;
        }
        String providedSecret = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(providedSecret) || !matches(expectedSecret, providedSecret)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant runtime shared secret");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String expectedSecret, String providedSecret) {
        byte[] expected = expectedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
