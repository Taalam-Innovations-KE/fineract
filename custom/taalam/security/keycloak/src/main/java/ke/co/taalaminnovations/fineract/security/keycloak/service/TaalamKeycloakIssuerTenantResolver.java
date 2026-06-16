/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaalamKeycloakIssuerTenantResolver {

    private static final String REALMS_PATH = "/realms/";

    private final TaalamKeycloakResourceServerProperties properties;
    private final TenantDetailsService tenantDetailsService;

    public FineractPlatformTenant resolveTenant(String issuer) {
        return tenantDetailsService.loadTenantById(tenantIdentifier(issuer));
    }

    public String tenantIdentifier(String issuer) {
        if (isBlank(issuer)) {
            throw new InvalidBearerTokenException("Missing issuer");
        }

        String keycloakBaseUrl = properties.normalizedKeycloakBaseUrl();
        if (isBlank(keycloakBaseUrl)) {
            throw new InvalidBearerTokenException("Keycloak base URL is not configured");
        }

        String normalizedIssuer = normalizeUrl(issuer);
        String issuerPrefix = keycloakBaseUrl + REALMS_PATH;
        if (!normalizedIssuer.startsWith(issuerPrefix)) {
            throw new InvalidBearerTokenException("Untrusted issuer");
        }

        String tenantIdentifier = normalizedIssuer.substring(issuerPrefix.length());
        if (isBlank(tenantIdentifier) || tenantIdentifier.contains("/")) {
            throw new InvalidBearerTokenException("Issuer does not identify exactly one tenant realm");
        }
        return tenantIdentifier;
    }

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
