/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import jakarta.ws.rs.core.Response;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantRuntimeAuthenticationVerifier {

    private final TaalamTenantRuntimeProperties properties;
    private final ObjectProvider<DaoAuthenticationProvider> authenticationProvider;

    public TenantRuntimeAuthenticationVerifier(TaalamTenantRuntimeProperties properties,
            @Qualifier("customAuthenticationProvider") ObjectProvider<DaoAuthenticationProvider> authenticationProvider) {
        this.properties = properties;
        this.authenticationProvider = authenticationProvider;
    }

    public boolean verifyIfEnabled(FineractPlatformTenant tenant) {
        if (!properties.isAuthenticationVerificationEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(properties.getAuthenticationVerificationUsername())
                || !StringUtils.hasText(properties.getAuthenticationVerificationPassword())) {
            throw new TenantRuntimeException(Response.Status.SERVICE_UNAVAILABLE,
                    "Tenant runtime authentication verification credentials are not configured");
        }
        DaoAuthenticationProvider provider = authenticationProvider.getIfAvailable();
        if (provider == null) {
            throw new TenantRuntimeException(Response.Status.SERVICE_UNAVAILABLE,
                    "Fineract authentication provider is not available for tenant runtime verification");
        }
        try {
            ThreadLocalContextUtil.setTenant(tenant);
            provider.authenticate(new UsernamePasswordAuthenticationToken(properties.getAuthenticationVerificationUsername(),
                    properties.getAuthenticationVerificationPassword()));
            return true;
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }
}
