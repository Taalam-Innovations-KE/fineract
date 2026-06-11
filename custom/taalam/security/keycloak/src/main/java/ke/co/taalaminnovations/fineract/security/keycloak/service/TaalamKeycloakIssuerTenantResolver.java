/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
