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
    private final OAuth2 oauth2 = new OAuth2();
    private boolean authenticationVerificationEnabled;
    private String authenticationVerificationUsername = "mifos";
    private String authenticationVerificationPassword = "password";

    @Getter
    @Setter
    public static class OAuth2 {

        private String issuerUri;
        private String jwkSetUri;
        private String audience = "fineract";
        private String requiredAuthority = "TENANT_RUNTIME_REGISTER";
    }
}
