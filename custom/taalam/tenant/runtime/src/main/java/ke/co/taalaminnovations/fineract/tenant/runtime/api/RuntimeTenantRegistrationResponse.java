/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record RuntimeTenantRegistrationResponse(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("database_name") String databaseName, @JsonProperty("registered") boolean registered,
        @JsonProperty("migrated") boolean migrated, @JsonProperty("refreshed") boolean refreshed,
        @JsonProperty("authentication_verified") boolean authenticationVerified, @JsonProperty("status") String status,
        @JsonProperty("completed_at") Instant completedAt) {
}
