/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class TaalamKeycloakIssuerTenantResolverTest {

    private final TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
    private final TaalamKeycloakResourceServerProperties properties = new TaalamKeycloakResourceServerProperties();

    @Test
    void derivesTenantIdentifierFromRealmIssuer() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        TaalamKeycloakIssuerTenantResolver underTest = new TaalamKeycloakIssuerTenantResolver(properties, tenantDetailsService);

        assertThat(underTest.tenantIdentifier("https://id.example.org/realms/acme")).isEqualTo("acme");
    }

    @Test
    void ignoresTrailingSlashOnBaseUrlAndIssuer() {
        properties.setKeycloakBaseUrl("https://id.example.org/");
        TaalamKeycloakIssuerTenantResolver underTest = new TaalamKeycloakIssuerTenantResolver(properties, tenantDetailsService);

        assertThat(underTest.tenantIdentifier("https://id.example.org/realms/default/")).isEqualTo("default");
    }

    @Test
    void resolvesTenantFromDerivedIdentifier() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "acme", "Acme", "UTC", null);
        when(tenantDetailsService.loadTenantById("acme")).thenReturn(tenant);
        TaalamKeycloakIssuerTenantResolver underTest = new TaalamKeycloakIssuerTenantResolver(properties, tenantDetailsService);

        assertThat(underTest.resolveTenant("https://id.example.org/realms/acme")).isSameAs(tenant);
        verify(tenantDetailsService).loadTenantById("acme");
    }

    @Test
    void rejectsIssuerOutsideConfiguredKeycloakBaseUrl() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        TaalamKeycloakIssuerTenantResolver underTest = new TaalamKeycloakIssuerTenantResolver(properties, tenantDetailsService);

        assertThatThrownBy(() -> underTest.tenantIdentifier("https://other.example.org/realms/acme"))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rejectsIssuerThatDoesNotIdentifyExactlyOneRealm() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        TaalamKeycloakIssuerTenantResolver underTest = new TaalamKeycloakIssuerTenantResolver(properties, tenantDetailsService);

        assertThatThrownBy(() -> underTest.tenantIdentifier("https://id.example.org/realms/acme/protocol/openid-connect"))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
