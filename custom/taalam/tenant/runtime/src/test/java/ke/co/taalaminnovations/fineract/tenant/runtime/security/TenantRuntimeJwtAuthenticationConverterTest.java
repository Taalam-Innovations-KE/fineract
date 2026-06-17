/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantRuntimeJwtAuthenticationConverterTest {

    private final TenantRuntimeJwtAuthenticationConverter underTest = new TenantRuntimeJwtAuthenticationConverter();

    @Test
    void extractsTenantRuntimeAuthorityFromRealmRoles() {
        Jwt jwt = jwt(Map.of("preferred_username", "tenant-management", "realm_access",
                Map.of("roles", List.of("TENANT_RUNTIME_REGISTER"))));

        JwtAuthenticationToken authentication = underTest.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("tenant-management");
        assertThat(authentication.getAuthorities()).extracting("authority").contains("TENANT_RUNTIME_REGISTER");
    }

    @Test
    void extractsTenantRuntimeAuthorityFromClientRolesAndScopes() {
        Jwt jwt = jwt(Map.of("azp", "tenant-management-service", "scope", "TENANT_RUNTIME_REGISTER profile",
                "resource_access", Map.of("fineract", Map.of("roles", List.of("TENANT_RUNTIME_REGISTER")))));

        JwtAuthenticationToken authentication = underTest.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("tenant-management-service");
        assertThat(authentication.getAuthorities()).extracting("authority").contains("TENANT_RUNTIME_REGISTER",
                "SCOPE_TENANT_RUNTIME_REGISTER");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).claims(values -> values.putAll(claims)).build();
    }
}
