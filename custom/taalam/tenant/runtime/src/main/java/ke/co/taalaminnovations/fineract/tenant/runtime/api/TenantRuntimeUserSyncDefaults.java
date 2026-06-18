/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TenantRuntimeUserSyncDefaults(@JsonProperty("office_id") Long officeId,
        @JsonProperty("office_external_id") String officeExternalId, @JsonProperty("role_names") List<String> roleNames) {
}
