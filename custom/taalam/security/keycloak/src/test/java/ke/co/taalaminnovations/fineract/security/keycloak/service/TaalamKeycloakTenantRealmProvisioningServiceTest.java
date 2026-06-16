/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
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
    private final ClientResource resourceServerClientResource = mock(ClientResource.class);
    private final ClientResource uiClientResource = mock(ClientResource.class);
    private final ProtocolMappersResource resourceServerProtocolMappersResource = mock(ProtocolMappersResource.class);
    private final ProtocolMappersResource uiProtocolMappersResource = mock(ProtocolMappersResource.class);
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
        when(resourceServerClientResource.getProtocolMappers()).thenReturn(resourceServerProtocolMappersResource);
        when(uiClientResource.getProtocolMappers()).thenReturn(uiProtocolMappersResource);
        when(clientsResource.create(any(ClientRepresentation.class)))
                .thenAnswer(invocation -> Response.status(Response.Status.CREATED).build());
        when(resourceServerProtocolMappersResource.createMapper(any(ProtocolMapperRepresentation.class)))
                .thenAnswer(invocation -> Response.status(Response.Status.CREATED).build());
        when(uiProtocolMappersResource.createMapper(any(ProtocolMapperRepresentation.class)))
                .thenAnswer(invocation -> Response.status(Response.Status.CREATED).build());
    }

    @Test
    void createsMissingRealmClientAndTokenMappers() {
        RuntimeTenantRegistrationRequest request = request();
        ClientRepresentation createdClient = client("kc-client-id", "fineract");
        when(realmResource.toRepresentation()).thenThrow(notFound()).thenReturn(realm("new_ke"));
        when(clientsResource.findByClientId("fineract")).thenReturn(List.of()).thenReturn(List.of(createdClient));
        when(clientsResource.get("kc-client-id")).thenReturn(resourceServerClientResource);
        when(resourceServerClientResource.toRepresentation()).thenReturn(createdClient);
        when(resourceServerProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of());

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
        org.mockito.Mockito.verify(resourceServerProtocolMappersResource, org.mockito.Mockito.times(3)).createMapper(mapperCaptor.capture());
        assertThat(mapperCaptor.getAllValues()).extracting(ProtocolMapperRepresentation::getName).containsExactly("fineract-audience",
                "fineract-username", "fineract-email");
        verify(clientsResource, never()).findByClientId("fineract-ui");
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
        when(clientsResource.get("kc-client-id")).thenReturn(resourceServerClientResource);
        when(resourceServerClientResource.toRepresentation()).thenReturn(existingClient);
        when(resourceServerProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of(existingMapper)).thenReturn(List.of())
                .thenReturn(List.of());

        underTest.ensureTenant(request);

        verify(realmResource).update(realm);
        verify(resourceServerClientResource).update(existingClient);
        verify(resourceServerProtocolMappersResource).update(eq("mapper-1"), any(ProtocolMapperRepresentation.class));
    }

    @Test
    void createsConfiguredUiClientWithPkceRedirectsLogoutAndAudienceMapper() {
        properties.getProvisioning().getUiClient().setBaseUrl("https://app.example.org/");
        RuntimeTenantRegistrationRequest request = request();
        ClientRepresentation resourceServerClient = client("kc-client-id", "fineract");
        ClientRepresentation createdUiClient = uiClient("kc-ui-client-id", "fineract-ui", "https://app.example.org");
        when(realmResource.toRepresentation()).thenReturn(realm("new_ke"));
        when(clientsResource.findByClientId("fineract")).thenReturn(List.of(resourceServerClient));
        when(clientsResource.findByClientId("fineract-ui")).thenReturn(List.of()).thenReturn(List.of(createdUiClient));
        when(clientsResource.get("kc-client-id")).thenReturn(resourceServerClientResource);
        when(clientsResource.get("kc-ui-client-id")).thenReturn(uiClientResource);
        when(resourceServerClientResource.toRepresentation()).thenReturn(resourceServerClient);
        when(uiClientResource.toRepresentation()).thenReturn(createdUiClient);
        when(resourceServerProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of());
        when(uiProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of());

        underTest.ensureTenant(request);

        ArgumentCaptor<ClientRepresentation> clientCaptor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clientsResource).create(clientCaptor.capture());
        ClientRepresentation uiClient = clientCaptor.getValue();
        assertThat(uiClient.getClientId()).isEqualTo("fineract-ui");
        assertThat(uiClient.isPublicClient()).isTrue();
        assertThat(uiClient.isStandardFlowEnabled()).isTrue();
        assertThat(uiClient.isDirectAccessGrantsEnabled()).isFalse();
        assertThat(uiClient.isImplicitFlowEnabled()).isFalse();
        assertThat(uiClient.isServiceAccountsEnabled()).isFalse();
        assertThat(uiClient.getRedirectUris()).containsExactly("https://app.example.org/api/auth/callback/keycloak");
        assertThat(uiClient.getWebOrigins()).containsExactly("https://app.example.org");
        assertThat(uiClient.getAttributes()).containsEntry("pkce.code.challenge.method", "S256")
                .containsEntry("post.logout.redirect.uris", "https://app.example.org/*");

        ArgumentCaptor<ProtocolMapperRepresentation> uiMapperCaptor = ArgumentCaptor.forClass(ProtocolMapperRepresentation.class);
        verify(uiProtocolMappersResource).createMapper(uiMapperCaptor.capture());
        assertThat(uiMapperCaptor.getValue().getName()).isEqualTo("fineract-audience");
        assertThat(uiMapperCaptor.getValue().getConfig()).containsEntry("included.client.audience", "fineract")
                .containsEntry("access.token.claim", "true");
    }

    @Test
    void repairsExistingUiClientWithoutDiscardingUnrelatedAttributes() {
        properties.getProvisioning().getUiClient().setBaseUrl("https://app.example.org");
        RuntimeTenantRegistrationRequest request = request();
        ClientRepresentation resourceServerClient = client("kc-client-id", "fineract");
        ClientRepresentation existingUiClient = client("kc-ui-client-id", "fineract-ui");
        existingUiClient.setRedirectUris(List.of("https://old.example.org/callback"));
        existingUiClient.setWebOrigins(List.of("https://old.example.org"));
        existingUiClient.setAttributes(Map.of("custom.attribute", "keep"));
        ProtocolMapperRepresentation existingUiAudienceMapper = new ProtocolMapperRepresentation();
        existingUiAudienceMapper.setId("ui-mapper-1");
        existingUiAudienceMapper.setName("fineract-audience");
        when(realmResource.toRepresentation()).thenReturn(realm("new_ke"));
        when(clientsResource.findByClientId("fineract")).thenReturn(List.of(resourceServerClient));
        when(clientsResource.findByClientId("fineract-ui")).thenReturn(List.of(existingUiClient));
        when(clientsResource.get("kc-client-id")).thenReturn(resourceServerClientResource);
        when(clientsResource.get("kc-ui-client-id")).thenReturn(uiClientResource);
        when(resourceServerClientResource.toRepresentation()).thenReturn(resourceServerClient);
        when(uiClientResource.toRepresentation()).thenReturn(existingUiClient);
        when(resourceServerProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of());
        when(uiProtocolMappersResource.getMappersPerProtocol("openid-connect")).thenReturn(List.of(existingUiAudienceMapper));

        underTest.ensureTenant(request);

        verify(uiClientResource).update(existingUiClient);
        assertThat(existingUiClient.isPublicClient()).isTrue();
        assertThat(existingUiClient.isDirectAccessGrantsEnabled()).isFalse();
        assertThat(existingUiClient.getRedirectUris()).containsExactly("https://app.example.org/api/auth/callback/keycloak");
        assertThat(existingUiClient.getWebOrigins()).containsExactly("https://app.example.org");
        assertThat(existingUiClient.getAttributes()).containsEntry("custom.attribute", "keep")
                .containsEntry("pkce.code.challenge.method", "S256")
                .containsEntry("post.logout.redirect.uris", "https://app.example.org/*");
        verify(uiProtocolMappersResource).update(eq("ui-mapper-1"), any(ProtocolMapperRepresentation.class));
    }

    @Test
    void rejectsInvalidUiClientBaseUrl() {
        properties.getProvisioning().getUiClient().setBaseUrl("not-a-url");

        assertThatThrownBy(() -> underTest.ensureTenant(request())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keycloak UI client base-url must be a valid absolute http(s) URL");
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

    private ClientRepresentation uiClient(String id, String clientId, String baseUrl) {
        ClientRepresentation client = new ClientRepresentation();
        client.setId(id);
        client.setClientId(clientId);
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setPublicClient(true);
        client.setBearerOnly(false);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setServiceAccountsEnabled(false);
        client.setFullScopeAllowed(true);
        client.setRedirectUris(List.of(baseUrl + "/api/auth/callback/keycloak"));
        client.setWebOrigins(List.of(baseUrl));
        client.setAttributes(Map.of("pkce.code.challenge.method", "S256", "post.logout.redirect.uris", baseUrl + "/*"));
        return client;
    }

    private WebApplicationException notFound() {
        return new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }
}
