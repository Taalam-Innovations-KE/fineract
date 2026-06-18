/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record TenantRuntimeUserSyncResponse(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("database_name") String databaseName, @JsonProperty("status") String status, @JsonProperty("requested") int requested,
        @JsonProperty("created") int created, @JsonProperty("updated") int updated, @JsonProperty("unchanged") int unchanged,
        @JsonProperty("conflicts") int conflicts, @JsonProperty("failed") int failed,
        @JsonProperty("results") List<TenantRuntimeUserSyncResult> results, @JsonProperty("completed_at") Instant completedAt) {
}
