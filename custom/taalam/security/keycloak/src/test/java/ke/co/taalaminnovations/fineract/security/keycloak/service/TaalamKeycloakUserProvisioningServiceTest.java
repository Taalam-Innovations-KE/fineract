/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
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
