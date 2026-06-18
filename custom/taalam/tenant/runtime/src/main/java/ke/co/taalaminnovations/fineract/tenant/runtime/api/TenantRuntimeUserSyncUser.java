/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TenantRuntimeUserSyncUser(@JsonProperty("source_user_id") String sourceUserId, @JsonProperty("username") String username,
        @JsonProperty("email") String email, @JsonProperty("first_name") String firstName, @JsonProperty("last_name") String lastName,
        @JsonProperty("office_id") Long officeId, @JsonProperty("office_external_id") String officeExternalId,
        @JsonProperty("role_names") List<String> roleNames, @JsonProperty("staff_id") Long staffId) {
}
