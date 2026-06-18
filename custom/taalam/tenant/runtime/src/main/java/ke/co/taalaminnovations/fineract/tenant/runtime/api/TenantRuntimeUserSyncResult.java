/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TenantRuntimeUserSyncResult(@JsonProperty("source_user_id") String sourceUserId, @JsonProperty("username") String username,
        @JsonProperty("email") String email, @JsonProperty("app_user_id") Long appUserId, @JsonProperty("local_status") String localStatus,
        @JsonProperty("identity_status") String identityStatus, @JsonProperty("message") String message) {
}
