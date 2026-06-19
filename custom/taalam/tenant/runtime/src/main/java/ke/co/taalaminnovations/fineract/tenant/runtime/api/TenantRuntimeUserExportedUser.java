/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TenantRuntimeUserExportedUser(@JsonProperty("source_user_id") String sourceUserId,
        @JsonProperty("fineract_user_id") Long fineractUserId, @JsonProperty("username") String username,
        @JsonProperty("email") String email, @JsonProperty("first_name") String firstName, @JsonProperty("last_name") String lastName,
        @JsonProperty("display_name") String displayName, @JsonProperty("enabled") boolean enabled,
        @JsonProperty("deleted") boolean deleted, @JsonProperty("active") boolean active, @JsonProperty("office_id") Long officeId,
        @JsonProperty("office_name") String officeName, @JsonProperty("office_external_id") String officeExternalId,
        @JsonProperty("staff_id") Long staffId, @JsonProperty("staff_display_name") String staffDisplayName,
        @JsonProperty("staff_external_id") String staffExternalId, @JsonProperty("role_names") List<String> roleNames) {
}
