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
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import org.junit.jupiter.api.Test;

class TaalamKeycloakUserProvisioningServiceTest {

    private final TaalamKeycloakResourceServerProperties properties = new TaalamKeycloakResourceServerProperties();
    private final TaalamKeycloakAdminClient adminClient = mock(TaalamKeycloakAdminClient.class);
    private final TaalamKeycloakUserProvisioningService underTest = new TaalamKeycloakUserProvisioningService(properties, adminClient);

    @Test
    void doesNotCallKeycloakWhenProvisioningIsDisabled() {
        underTest.provisionUser(request());

        verifyNoInteractions(adminClient);
    }

    @Test
    void callsKeycloakWhenProvisioningIsEnabled() {
        properties.getProvisioning().setEnabled(true);

        underTest.provisionUser(request());

        verify(adminClient).provisionUser(request());
    }

    private TaalamKeycloakUserProvisioningRequest request() {
        return new TaalamKeycloakUserProvisioningRequest("default", "jane", "jane@example.org", "Jane", "Doe");
    }
}
