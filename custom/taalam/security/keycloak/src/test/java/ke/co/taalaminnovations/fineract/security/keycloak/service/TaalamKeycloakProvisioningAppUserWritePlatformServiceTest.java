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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.useradministration.service.AppUserWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TaalamKeycloakProvisioningAppUserWritePlatformServiceTest {

    private final AppUserWritePlatformService delegate = mock(AppUserWritePlatformService.class);
    private final TaalamKeycloakUserProvisioningService provisioningService = mock(TaalamKeycloakUserProvisioningService.class);
    private final TaalamKeycloakProvisioningAppUserWritePlatformService underTest = new TaalamKeycloakProvisioningAppUserWritePlatformService(
            delegate, provisioningService);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        ThreadLocalContextUtil.reset();
    }

    @Test
    void provisionsCreatedUserOnlyAfterTransactionCommit() {
        final JsonCommand command = userCommand();
        final CommandProcessingResult result = new CommandProcessingResultBuilder().withEntityId(10L).build();
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        TransactionSynchronizationManager.initSynchronization();
        when(delegate.createUser(command)).thenReturn(result);

        assertThat(underTest.createUser(command)).isSameAs(result);

        verify(delegate).createUser(command);
        verifyNoInteractions(provisioningService);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        final ArgumentCaptor<TaalamKeycloakUserProvisioningRequest> requestCaptor = ArgumentCaptor
                .forClass(TaalamKeycloakUserProvisioningRequest.class);
        verify(provisioningService).provisionUser(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .isEqualTo(new TaalamKeycloakUserProvisioningRequest("default", "jane", "jane@example.org", "Jane", "Doe"));
    }

    private JsonCommand userCommand() {
        final FromJsonHelper jsonHelper = new FromJsonHelper();
        final JsonElement parsed = jsonHelper
                .parse("{\"username\":\"jane\",\"email\":\"jane@example.org\",\"firstname\":\"Jane\",\"lastname\":\"Doe\"}");
        return new JsonCommand(null, parsed, jsonHelper);
    }
}
