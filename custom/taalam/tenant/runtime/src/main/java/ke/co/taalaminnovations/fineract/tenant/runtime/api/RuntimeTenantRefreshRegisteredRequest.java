/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RuntimeTenantRefreshRegisteredRequest(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("display_name") String displayName, @JsonProperty("timezone") String timezone,
        @JsonProperty("database_name") String databaseName) {
}
