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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mockito.ArgumentCaptor;

class TaalamKeycloakTenantRealmProvisioningServiceTest {

    private final TaalamKeycloakAdminClientFactory keycloakClientFactory = mock(TaalamKeycloakAdminClientFactory.class);
    private final Keycloak keycloak = mock(Keycloak.class);
    private final RealmsResource realmsResource = mock(RealmsResource.class);
    private final RealmResource realmResource = mock(RealmResource.class);
    private final ClientsResource clientsResource = mock(ClientsResource.class);
    private final ClientResource clientResource = mock(ClientResource.class);
    private final ProtocolMappersResource protocolMappersResource = mock(ProtocolMappersResource.class);
    private final TaalamKeycloakResourceServerProperties properties = new TaalamKeycloakResourceServerProperties();
    private final TaalamKeycloakTenantRealmProvisioningService underTest = new TaalamKeycloakTenantRealmProvisioningService(properties,
            keycloakClientFactory);

    @BeforeEach
    void setUp() {
        properties.setKeycloakBaseUrl("https://id.example.org");
        properties.getProvisioning().setAdminClientId("fineract-admin");
        properties.getProvisioning().setAdminClientSecret("secret");
        when(keycloakClientFactory.create("https://id.example.org", "master", "fineract-admin", "secret")).thenReturn(keycloak);
        when(keycloak.realms()).thenReturn(realmsResource);
        when(keycloak.realm("new_ke")).thenReturn(realmResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        when(clientResource.getProtocolMappers()).thenReturn(protocolMappersResource);
        when(clientsResource.create(any(ClientRepresentation.class)))
                .thenAnswer(invocation -> Response.status(Response.Status.CREATED).build());
        when(protocolMappersResource.createMapper(any(ProtocolMapperRepresentation.class)))
                .thenAnswer(invocation -> Response.status(Response.Status.CREATED).build());
    }

    @Test
    void createsMissingRealmClientAndTokenMappers() {
        RuntimeTenantRegistrationRequest request = request();
        ClientRepresentation createdClient = client("kc-client-id", "fineract");
        when(realmResource.toRepresentation()).thenThrow(notFound()).thenReturn(realm("new_ke"));
        when(clientsResource.findByClientId("fineract")).thenReturn(List.of()).thenReturn(List.of(createdClient));
        when(clientsResource.get("kc-client-id")).thenReturn(clientResource);
        when(clientResource.toRepresentation()).thenReturn(createdClient);
        when(protocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of());

        underTest.ensureTenant(request);

        ArgumentCaptor<RealmRepresentation> realmCaptor = ArgumentCaptor.forClass(RealmRepresentation.class);
        verify(realmsResource).create(realmCaptor.capture());
        assertThat(realmCaptor.getValue().getRealm()).isEqualTo("new_ke");
        assertThat(realmCaptor.getValue().isEnabled()).isTrue();
        assertThat(realmCaptor.getValue().isEditUsernameAllowed()).isTrue();

        ArgumentCaptor<ClientRepresentation> clientCaptor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clientsResource).create(clientCaptor.capture());
        assertThat(clientCaptor.getValue().getClientId()).isEqualTo("fineract");
        assertThat(clientCaptor.getValue().getClientAuthenticatorType()).isEqualTo("client-secret");
        assertThat(clientCaptor.getValue().isDirectAccessGrantsEnabled()).isTrue();

        ArgumentCaptor<ProtocolMapperRepresentation> mapperCaptor = ArgumentCaptor.forClass(ProtocolMapperRepresentation.class);
        org.mockito.Mockito.verify(protocolMappersResource, org.mockito.Mockito.times(3)).createMapper(mapperCaptor.capture());
        assertThat(mapperCaptor.getAllValues()).extracting(ProtocolMapperRepresentation::getName).containsExactly("fineract-audience",
                "fineract-username", "fineract-email");
        verify(keycloak).close();
    }

    @Test
    void repairsExistingRealmAndClient() {
        RuntimeTenantRegistrationRequest request = request();
        RealmRepresentation realm = realm("new_ke");
        realm.setEnabled(false);
        realm.setEditUsernameAllowed(false);
        realm.setLoginWithEmailAllowed(false);
        realm.setDuplicateEmailsAllowed(true);
        ClientRepresentation existingClient = client("kc-client-id", "fineract");
        existingClient.setEnabled(false);
        existingClient.setDirectAccessGrantsEnabled(false);
        ProtocolMapperRepresentation existingMapper = new ProtocolMapperRepresentation();
        existingMapper.setId("mapper-1");
        existingMapper.setName("fineract-audience");
        when(realmResource.toRepresentation()).thenReturn(realm);
        when(clientsResource.findByClientId("fineract")).thenReturn(List.of(existingClient));
        when(clientsResource.get("kc-client-id")).thenReturn(clientResource);
        when(clientResource.toRepresentation()).thenReturn(existingClient);
        when(protocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of(existingMapper)).thenReturn(List.of())
                .thenReturn(List.of());

        underTest.ensureTenant(request);

        verify(realmResource).update(realm);
        verify(clientResource).update(existingClient);
        verify(protocolMappersResource).update(org.mockito.Mockito.eq("mapper-1"), any(ProtocolMapperRepresentation.class));
    }

    private RuntimeTenantRegistrationRequest request() {
        return new RuntimeTenantRegistrationRequest("new_ke", "New Kenya Tenant", "Africa/Nairobi", "POSTGRESQL", "localhost", 5432,
                "fineract_new_ke", "fineract_new_ke", "tenant-password", null);
    }

    private RealmRepresentation realm(String realmName) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(realmName);
        realm.setEnabled(true);
        realm.setEditUsernameAllowed(true);
        realm.setLoginWithEmailAllowed(true);
        realm.setDuplicateEmailsAllowed(false);
        return realm;
    }

    private ClientRepresentation client(String id, String clientId) {
        ClientRepresentation client = new ClientRepresentation();
        client.setId(id);
        client.setClientId(clientId);
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setClientAuthenticatorType("client-secret");
        client.setPublicClient(false);
        client.setBearerOnly(false);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setImplicitFlowEnabled(false);
        return client;
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }
}
