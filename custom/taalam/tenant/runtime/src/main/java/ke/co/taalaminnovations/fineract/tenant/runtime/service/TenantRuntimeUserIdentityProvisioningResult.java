/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

public record TenantRuntimeUserIdentityProvisioningResult(String status, String message) {

    public static TenantRuntimeUserIdentityProvisioningResult skipped(String message) {
        return new TenantRuntimeUserIdentityProvisioningResult("SKIPPED", message);
    }
}
