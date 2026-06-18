/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TenantRuntimeUserSyncRequest(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("database_name") String databaseName, @JsonProperty("dry_run") Boolean dryRun,
        @JsonProperty("provision_identity") Boolean provisionIdentity,
        @JsonProperty("user_defaults") TenantRuntimeUserSyncDefaults userDefaults,
        @JsonProperty("users") List<TenantRuntimeUserSyncUser> users) {
}
