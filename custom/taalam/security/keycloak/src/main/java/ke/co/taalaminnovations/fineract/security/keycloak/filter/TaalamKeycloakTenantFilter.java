/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.filter;

import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakIssuerTenantResolver;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class TaalamKeycloakTenantFilter extends OncePerRequestFilter {

    private final BearerTokenResolver bearerTokenResolver;
    private final TaalamKeycloakIssuerTenantResolver tenantResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = bearerTokenResolver.resolve(request);
        try {
            if (token != null) {
                String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
                FineractPlatformTenant tenant = tenantResolver.resolveTenant(issuer);
                ThreadLocalContextUtil.setTenant(tenant);
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }
}
