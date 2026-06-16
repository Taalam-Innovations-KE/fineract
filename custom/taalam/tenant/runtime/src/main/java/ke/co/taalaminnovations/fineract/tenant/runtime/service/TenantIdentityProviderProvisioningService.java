/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;

public interface TenantIdentityProviderProvisioningService {

    void ensureTenant(RuntimeTenantRegistrationRequest request);
}
