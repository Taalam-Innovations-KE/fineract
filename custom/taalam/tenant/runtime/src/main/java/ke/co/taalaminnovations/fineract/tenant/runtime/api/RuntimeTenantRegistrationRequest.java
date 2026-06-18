/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RuntimeTenantRegistrationRequest(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("display_name") String displayName, @JsonProperty("timezone") String timezone,
        @JsonProperty("database_type") String databaseType, @JsonProperty("database_host") String databaseHost,
        @JsonProperty("database_port") Integer databasePort, @JsonProperty("database_name") String databaseName,
        @JsonProperty("runtime_username") String runtimeUsername, @JsonProperty("runtime_password") String runtimePassword,
        @JsonProperty("connection_parameters") String connectionParameters,
        @JsonProperty("user_sync") TenantRuntimeUserSyncRequest userSync) {

    public RuntimeTenantRegistrationRequest(String tenantIdentifier, String displayName, String timezone, String databaseType,
            String databaseHost, Integer databasePort, String databaseName, String runtimeUsername, String runtimePassword,
            String connectionParameters) {
        this(tenantIdentifier, displayName, timezone, databaseType, databaseHost, databasePort, databaseName, runtimeUsername,
                runtimePassword, connectionParameters, null);
    }
}
