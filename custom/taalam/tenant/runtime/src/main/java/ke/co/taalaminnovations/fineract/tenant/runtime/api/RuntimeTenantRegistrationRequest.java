/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RuntimeTenantRegistrationRequest(@JsonProperty("tenant_identifier") String tenantIdentifier,
        @JsonProperty("display_name") String displayName, @JsonProperty("timezone") String timezone,
        @JsonProperty("database_type") String databaseType, @JsonProperty("database_host") String databaseHost,
        @JsonProperty("database_port") Integer databasePort, @JsonProperty("database_name") String databaseName,
        @JsonProperty("runtime_username") String runtimeUsername, @JsonProperty("runtime_password") String runtimePassword,
        @JsonProperty("connection_parameters") String connectionParameters) {
}
