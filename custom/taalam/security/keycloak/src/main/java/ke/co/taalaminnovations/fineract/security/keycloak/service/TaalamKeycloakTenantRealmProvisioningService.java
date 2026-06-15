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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties.Provisioning;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantIdentityProviderProvisioningService;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TaalamKeycloakTenantRealmProvisioningService implements TenantIdentityProviderProvisioningService {

    private static final String OPENID_CONNECT = "openid-connect";
    private static final String AUDIENCE_MAPPER = "oidc-audience-mapper";
    private static final String USER_MODEL_PROPERTY_MAPPER = "oidc-usermodel-property-mapper";
    private static final String FINERACT_AUDIENCE_MAPPER_NAME = "fineract-audience";
    private static final String FINERACT_USERNAME_MAPPER_NAME = "fineract-username";
    private static final String FINERACT_EMAIL_MAPPER_NAME = "fineract-email";

    private final TaalamKeycloakResourceServerProperties properties;
    private final TaalamKeycloakAdminClientFactory keycloakClientFactory;

    @Override
    public void ensureTenant(final RuntimeTenantRegistrationRequest request) {
        validateConfiguration();

        final Provisioning provisioning = properties.getProvisioning();
        try (Keycloak keycloak = keycloakClientFactory.create(properties.normalizedKeycloakBaseUrl(), provisioning.getAdminRealm(),
                provisioning.getAdminClientId(), provisioning.getAdminClientSecret())) {
            final RealmResource realm = ensureRealm(keycloak, request);
            final ClientResource client = ensureClient(realm.clients(), properties.getAudience());
            ensureProtocolMapper(client, audienceMapper(properties.getAudience()));
            ensureProtocolMapper(client, userPropertyMapper(FINERACT_USERNAME_MAPPER_NAME, "username", properties.getUsernameClaim()));
            ensureProtocolMapper(client, userPropertyMapper(FINERACT_EMAIL_MAPPER_NAME, "email", properties.getEmailClaim()));
        }
    }

    private void validateConfiguration() {
        final Provisioning provisioning = properties.getProvisioning();
        if (!StringUtils.hasText(properties.normalizedKeycloakBaseUrl())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but keycloak-base-url is not configured");
        }
        if (!StringUtils.hasText(properties.getAudience())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but audience is not configured");
        }
        if (!StringUtils.hasText(properties.getUsernameClaim())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but username-claim is not configured");
        }
        if (!StringUtils.hasText(properties.getEmailClaim())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but email-claim is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminRealm())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but admin-realm is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminClientId())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but admin-client-id is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminClientSecret())) {
            throw new IllegalStateException("Keycloak realm provisioning is enabled but admin-client-secret is not configured");
        }
    }

    private RealmResource ensureRealm(final Keycloak keycloak, final RuntimeTenantRegistrationRequest request) {
        final String tenantIdentifier = request.tenantIdentifier();
        Optional<RealmRepresentation> existingRealm = findRealm(keycloak, tenantIdentifier);
        if (existingRealm.isEmpty()) {
            createRealm(keycloak, request);
        }

        final RealmResource realm = keycloak.realm(tenantIdentifier);
        final RealmRepresentation representation = realm.toRepresentation();
        boolean changed = false;
        if (!Boolean.TRUE.equals(representation.isEnabled())) {
            representation.setEnabled(true);
            changed = true;
        }
        if (!Boolean.TRUE.equals(representation.isEditUsernameAllowed())) {
            representation.setEditUsernameAllowed(true);
            changed = true;
        }
        if (!Boolean.TRUE.equals(representation.isLoginWithEmailAllowed())) {
            representation.setLoginWithEmailAllowed(true);
            changed = true;
        }
        if (Boolean.TRUE.equals(representation.isDuplicateEmailsAllowed())) {
            representation.setDuplicateEmailsAllowed(false);
            changed = true;
        }
        if (changed) {
            realm.update(representation);
        }
        return realm;
    }

    private Optional<RealmRepresentation> findRealm(final Keycloak keycloak, final String realmName) {
        try {
            return Optional.ofNullable(keycloak.realm(realmName).toRepresentation());
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private void createRealm(final Keycloak keycloak, final RuntimeTenantRegistrationRequest request) {
        final RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(request.tenantIdentifier());
        realm.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName() : request.tenantIdentifier());
        realm.setEnabled(true);
        realm.setEditUsernameAllowed(true);
        realm.setLoginWithEmailAllowed(true);
        realm.setDuplicateEmailsAllowed(false);

        try {
            keycloak.realms().create(realm);
        } catch (WebApplicationException e) {
            if (e.getResponse() == null || e.getResponse().getStatus() != Response.Status.CONFLICT.getStatusCode()) {
                throw e;
            }
        }
    }

    private ClientResource ensureClient(final ClientsResource clients, final String clientId) {
        Optional<ClientRepresentation> existingClient = findSingleClient(clients, clientId);
        if (existingClient.isEmpty()) {
            createClient(clients, clientId);
            existingClient = findSingleClient(clients, clientId);
        }

        final ClientRepresentation clientRepresentation = existingClient
                .orElseThrow(() -> new IllegalStateException("Keycloak client " + clientId + " was not found after creation"));
        final ClientResource client = clients.get(clientRepresentation.getId());
        final ClientRepresentation current = client.toRepresentation();
        boolean changed = false;
        if (!Boolean.TRUE.equals(current.isEnabled())) {
            current.setEnabled(true);
            changed = true;
        }
        if (!OPENID_CONNECT.equals(current.getProtocol())) {
            current.setProtocol(OPENID_CONNECT);
            changed = true;
        }
        if (!"client-secret".equals(current.getClientAuthenticatorType())) {
            current.setClientAuthenticatorType("client-secret");
            changed = true;
        }
        if (!Boolean.FALSE.equals(current.isPublicClient())) {
            current.setPublicClient(false);
            changed = true;
        }
        if (!Boolean.FALSE.equals(current.isBearerOnly())) {
            current.setBearerOnly(false);
            changed = true;
        }
        if (!Boolean.TRUE.equals(current.isStandardFlowEnabled())) {
            current.setStandardFlowEnabled(true);
            changed = true;
        }
        if (!Boolean.TRUE.equals(current.isDirectAccessGrantsEnabled())) {
            current.setDirectAccessGrantsEnabled(true);
            changed = true;
        }
        if (!Boolean.FALSE.equals(current.isImplicitFlowEnabled())) {
            current.setImplicitFlowEnabled(false);
            changed = true;
        }
        if (changed) {
            client.update(current);
        }
        return client;
    }

    private Optional<ClientRepresentation> findSingleClient(final ClientsResource clients, final String clientId) {
        final List<ClientRepresentation> matches = clients.findByClientId(clientId);
        final List<ClientRepresentation> safeMatches = matches == null ? List.of() : matches;
        if (safeMatches.size() > 1) {
            throw new IllegalStateException("More than one Keycloak client matched clientId [" + clientId + "]");
        }
        return safeMatches.stream().findFirst();
    }

    private void createClient(final ClientsResource clients, final String clientId) {
        try (Response response = clients.create(clientRepresentation(clientId))) {
            final int status = response.getStatus();
            if (status != Response.Status.CREATED.getStatusCode() && status != Response.Status.NO_CONTENT.getStatusCode()
                    && status != Response.Status.CONFLICT.getStatusCode()) {
                throw new IllegalStateException("Unexpected Keycloak client creation status: " + status);
            }
        }
    }

    private ClientRepresentation clientRepresentation(final String clientId) {
        final ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setName("Fineract Resource Server");
        client.setProtocol(OPENID_CONNECT);
        client.setClientAuthenticatorType("client-secret");
        client.setEnabled(true);
        client.setPublicClient(false);
        client.setBearerOnly(false);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setImplicitFlowEnabled(false);
        client.setServiceAccountsEnabled(false);
        client.setFullScopeAllowed(true);
        return client;
    }

    private void ensureProtocolMapper(final ClientResource client, final ProtocolMapperRepresentation expected) {
        final ProtocolMappersResource mappers = client.getProtocolMappers();
        final List<ProtocolMapperRepresentation> existingMappers = Optional.ofNullable(mappers.getMappersPerProtocol(OPENID_CONNECT))
                .orElseGet(List::of);
        final List<ProtocolMapperRepresentation> matches = existingMappers.stream()
                .filter(mapper -> Objects.equals(mapper.getName(), expected.getName())).toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("More than one Keycloak protocol mapper matched name [" + expected.getName() + "]");
        }
        if (matches.isEmpty()) {
            createProtocolMapper(mappers, expected);
            return;
        }

        final ProtocolMapperRepresentation existing = matches.get(0);
        expected.setId(existing.getId());
        mappers.update(existing.getId(), expected);
    }

    private void createProtocolMapper(final ProtocolMappersResource mappers, final ProtocolMapperRepresentation mapper) {
        try (Response response = mappers.createMapper(mapper)) {
            final int status = response.getStatus();
            if (status != Response.Status.CREATED.getStatusCode() && status != Response.Status.NO_CONTENT.getStatusCode()
                    && status != Response.Status.CONFLICT.getStatusCode()) {
                throw new IllegalStateException("Unexpected Keycloak protocol mapper creation status: " + status);
            }
        }
    }

    private ProtocolMapperRepresentation audienceMapper(final String audience) {
        final ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName(FINERACT_AUDIENCE_MAPPER_NAME);
        mapper.setProtocol(OPENID_CONNECT);
        mapper.setProtocolMapper(AUDIENCE_MAPPER);
        mapper.setConfig(Map.of("included.client.audience", audience, "id.token.claim", "false", "access.token.claim", "true"));
        return mapper;
    }

    private ProtocolMapperRepresentation userPropertyMapper(final String name, final String userProperty, final String claimName) {
        final ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName(name);
        mapper.setProtocol(OPENID_CONNECT);
        mapper.setProtocolMapper(USER_MODEL_PROPERTY_MAPPER);
        mapper.setConfig(Map.of("user.attribute", userProperty, "claim.name", claimName, "jsonType.label", "String", "id.token.claim",
                "true", "access.token.claim", "true", "userinfo.token.claim", "true"));
        return mapper;
    }
}
