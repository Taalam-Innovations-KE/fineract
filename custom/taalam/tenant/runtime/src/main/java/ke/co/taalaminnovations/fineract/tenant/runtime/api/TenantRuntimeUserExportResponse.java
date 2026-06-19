/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record TenantRuntimeUserExportResponse(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("database_name") String databaseName, @JsonProperty("total_fineract_users") int totalFineractUsers,
        @JsonProperty("exported") int exported, @JsonProperty("skipped") int skipped,
        @JsonProperty("users") List<TenantRuntimeUserExportedUser> users, @JsonProperty("exported_at") Instant exportedAt) {
}
