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
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;

public record RuntimeTenantConnection(RuntimeTenantRegistrationRequest request, String encryptedPassword, String masterPasswordHash) {

    public FineractPlatformTenant toTenant(Long connectionId) {
        FineractPlatformTenantConnection connection = new FineractPlatformTenantConnection(connectionId, request.databaseName(),
                request.databaseHost(), String.valueOf(request.databasePort()), request.connectionParameters(), request.runtimeUsername(),
                encryptedPassword, true, 5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null, null,
                null, masterPasswordHash);
        return new FineractPlatformTenant(-1L, request.tenantIdentifier(), request.displayName(), request.timezone(), connection);
    }
}
