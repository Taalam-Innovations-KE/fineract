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
}
