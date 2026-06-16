/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;

class TenantRuntimeAuthenticationVerifierTest {

    @AfterEach
    void resetThreadLocalContext() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void authenticatesWithTheProvisionedTenantInContext() {
        TaalamTenantRuntimeProperties properties = new TaalamTenantRuntimeProperties();
        properties.setAuthenticationVerificationEnabled(true);
        properties.setAuthenticationVerificationUsername("mifos");
        properties.setAuthenticationVerificationPassword("password");
        DaoAuthenticationProvider authenticationProvider = mock(DaoAuthenticationProvider.class);
        when(authenticationProvider.authenticate(any())).thenAnswer(invocation -> {
            Authentication authentication = invocation.getArgument(0);
            assertThat(ThreadLocalContextUtil.getTenant().getTenantIdentifier()).isEqualTo("new_ke");
            assertThat(authentication.getName()).isEqualTo("mifos");
            assertThat(authentication.getCredentials()).isEqualTo("password");
            return new UsernamePasswordAuthenticationToken("mifos", "password");
        });
        ObjectProvider<DaoAuthenticationProvider> provider = mockProvider(authenticationProvider);
        TenantRuntimeAuthenticationVerifier underTest = new TenantRuntimeAuthenticationVerifier(properties, provider);

        boolean verified = underTest.verifyIfEnabled(tenant("new_ke"));

        assertThat(verified).isTrue();
        assertThat(ThreadLocalContextUtil.getTenant()).isNull();
        verify(authenticationProvider).authenticate(argThat(authentication -> "mifos".equals(authentication.getName())));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DaoAuthenticationProvider> mockProvider(DaoAuthenticationProvider authenticationProvider) {
        ObjectProvider<DaoAuthenticationProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(authenticationProvider);
        return provider;
    }

    private FineractPlatformTenant tenant(String identifier) {
        return new FineractPlatformTenant(1L, identifier, "New Tenant", "Africa/Nairobi",
                new FineractPlatformTenantConnection(1L, "fineract_new_ke", "localhost", "5432", null, "fineract_new_ke", "encrypted", true,
                        5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null, null, null, "hash"));
    }
}
