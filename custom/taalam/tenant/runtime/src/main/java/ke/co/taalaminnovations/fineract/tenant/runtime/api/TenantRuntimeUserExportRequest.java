/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

public record TenantRuntimeUserExportRequest(String tenantIdentifier, String databaseName, Boolean includeDisabled, Boolean includeDeleted,
        Boolean includeSystemUsers) {
}
