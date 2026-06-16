/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;

public record RuntimeTenantConnection(RuntimeTenantRegistrationRequest request, String encryptedPassword, String masterPasswordHash) {

    public FineractPlatformTenant toTenant(Long connectionId) {
        FineractPlatformTenantConnection connection = new FineractPlatformTenantConnection(connectionId, request.databaseName(),
                request.databaseHost(), String.valueOf(request.databasePort()), request.connectionParameters(), request.runtimeUsername(),
                encryptedPassword, true, 5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null, null,
                null, masterPasswordHash);
        return new FineractPlatformTenant(-1L, request.tenantIdentifier(), request.displayName(), request.timezone(), connection);
    }
}
