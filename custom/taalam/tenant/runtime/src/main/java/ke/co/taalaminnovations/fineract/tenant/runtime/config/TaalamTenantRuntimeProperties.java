/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taalam.tenant.runtime")
@Getter
@Setter
public class TaalamTenantRuntimeProperties {

    private boolean enabled;
    private String sharedSecret;
    private boolean authenticationVerificationEnabled;
    private String authenticationVerificationUsername = "mifos";
    private String authenticationVerificationPassword = "password";

    /**
     * OAuth2/OIDC validation for the tenant-runtime endpoint. When a JWK set URI (or issuer URI) is configured the
     * endpoint authenticates callers with a Keycloak-issued JWT (client-credentials) instead of the shared secret.
     */
    private String jwkSetUri;
    private String issuerUri;
    private String audience;
}
