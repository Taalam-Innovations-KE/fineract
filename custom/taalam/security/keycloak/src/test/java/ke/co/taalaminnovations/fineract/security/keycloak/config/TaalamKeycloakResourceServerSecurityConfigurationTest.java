/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakAuthenticationManagerResolver;
import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakIssuerTenantResolver;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreHelper;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.apache.fineract.infrastructure.jobs.filter.ProgressiveLoanModelCheckerFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class TaalamKeycloakResourceServerSecurityConfigurationTest {

    @Test
    void exposesPasswordEncoderRequiredByFineractUserServices() {
        TaalamKeycloakResourceServerSecurityConfiguration underTest = new TaalamKeycloakResourceServerSecurityConfiguration(
                mock(TaalamKeycloakAuthenticationManagerResolver.class), mock(TaalamKeycloakIssuerTenantResolver.class),
                mock(BusinessDateReadPlatformService.class), mock(MDCWrapper.class), mock(IdempotencyStoreHelper.class),
                mock(FineractRequestContextHolder.class), mock(FineractProperties.class), mock(ProgressiveLoanModelCheckerFilter.class));

        PasswordEncoder passwordEncoder = underTest.passwordEncoder();

        assertThat(passwordEncoder.matches("secret", passwordEncoder.encode("secret"))).isTrue();
    }
}
