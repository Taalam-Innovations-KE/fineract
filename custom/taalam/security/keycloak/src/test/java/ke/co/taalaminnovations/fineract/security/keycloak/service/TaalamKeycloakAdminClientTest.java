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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

class TaalamKeycloakAdminClientTest {

    private final TaalamKeycloakAdminClientFactory keycloakClientFactory = mock(TaalamKeycloakAdminClientFactory.class);
    private final Keycloak keycloak = mock(Keycloak.class);
    private final RealmResource realmResource = mock(RealmResource.class);
    private final UsersResource usersResource = mock(UsersResource.class);
    private final UserResource userResource = mock(UserResource.class);
    private final TaalamKeycloakResourceServerProperties properties = new TaalamKeycloakResourceServerProperties();
    private final TaalamKeycloakAdminClient underTest = new TaalamKeycloakAdminClient(properties, keycloakClientFactory);

    @BeforeEach
    void setUp() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        properties.getProvisioning().setAdminClientId("fineract-admin");
        properties.getProvisioning().setAdminClientSecret("secret");
        when(keycloakClientFactory.create("https://id.example.org", "master", "fineract-admin", "secret")).thenReturn(keycloak);
        when(keycloak.realm("default")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void updatesUsernameForExistingKeycloakUserMatchedByEmailAndSendsSetupEmail() {
        final UserRepresentation existingUser = new UserRepresentation();
        existingUser.setId("kc-user-1");
        existingUser.setUsername("old-jane");
        existingUser.setEmail("jane@example.org");
        when(usersResource.searchByEmail("jane@example.org", true)).thenReturn(List.of(existingUser));
        when(usersResource.get("kc-user-1")).thenReturn(userResource);

        underTest.provisionUser(new TaalamKeycloakUserProvisioningRequest("default", "jane", "jane@example.org", "Jane", "Doe"));

        verify(usersResource).searchByEmail("jane@example.org", true);
        verify(usersResource, never()).searchByUsername("jane", true);
        verify(usersResource, never()).create(org.mockito.ArgumentMatchers.any(UserRepresentation.class));

        final ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("jane");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("jane@example.org");
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(userCaptor.getValue().getLastName()).isEqualTo("Doe");
        assertThat(userCaptor.getValue().getRequiredActions()).containsExactly("UPDATE_PASSWORD");

        verify(userResource).executeActionsEmail(List.of("UPDATE_PASSWORD"), 43_200);
        verify(keycloak).close();
    }
}
