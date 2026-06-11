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

import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties.Provisioning;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TaalamKeycloakAdminClient {

    private final TaalamKeycloakResourceServerProperties properties;
    private final TaalamKeycloakAdminClientFactory keycloakClientFactory;

    public void provisionUser(final TaalamKeycloakUserProvisioningRequest request) {
        final Provisioning provisioning = properties.getProvisioning();
        validateConfiguration(provisioning);

        try (Keycloak keycloak = keycloakClientFactory.create(properties.normalizedKeycloakBaseUrl(), provisioning.getAdminRealm(),
                provisioning.getAdminClientId(), provisioning.getAdminClientSecret())) {
            final UsersResource users = keycloak.realm(request.tenantIdentifier()).users();
            final Optional<UserRepresentation> existingUserByEmail = findUserByEmail(users, request.email());

            if (existingUserByEmail.isPresent()) {
                final UserRepresentation keycloakUser = existingUserByEmail.get();
                if (provisioning.isSyncUsernameOnEmailMatch()) {
                    updateUser(users, keycloakUser.getId(), request);
                }
                sendRequiredActionsEmail(users, keycloakUser.getId());
                return;
            }

            final Optional<UserRepresentation> existingUserByUsername = findUserByUsername(users, request.username());
            if (existingUserByUsername.isPresent()) {
                final UserRepresentation keycloakUser = existingUserByUsername.get();
                updateUser(users, keycloakUser.getId(), request);
                sendRequiredActionsEmail(users, keycloakUser.getId());
                return;
            }

            final String keycloakUserId = createUser(users, request);
            sendRequiredActionsEmail(users, keycloakUserId);
        }
    }

    private void validateConfiguration(final Provisioning provisioning) {
        if (!StringUtils.hasText(properties.normalizedKeycloakBaseUrl())) {
            throw new IllegalStateException("Keycloak provisioning is enabled but keycloak-base-url is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminRealm())) {
            throw new IllegalStateException("Keycloak provisioning is enabled but admin-realm is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminClientId())) {
            throw new IllegalStateException("Keycloak provisioning is enabled but admin-client-id is not configured");
        }
        if (!StringUtils.hasText(provisioning.getAdminClientSecret())) {
            throw new IllegalStateException("Keycloak provisioning is enabled but admin-client-secret is not configured");
        }
    }

    private Optional<UserRepresentation> findUserByEmail(final UsersResource users, final String email) {
        return findSingleUser(users.searchByEmail(email, true), "email", email);
    }

    private Optional<UserRepresentation> findUserByUsername(final UsersResource users, final String username) {
        return findSingleUser(users.searchByUsername(username, true), "username", username);
    }

    private Optional<UserRepresentation> findSingleUser(final List<UserRepresentation> users, final String field, final String value) {
        final List<UserRepresentation> matches = users == null ? List.of() : users;
        if (matches.size() > 1) {
            throw new IllegalStateException("More than one Keycloak user matched " + field + " [" + value + "]");
        }
        return matches.stream().findFirst();
    }

    private String createUser(final UsersResource users, final TaalamKeycloakUserProvisioningRequest request) {
        try (Response response = users.create(userRepresentation(request))) {
            final int status = response.getStatus();
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                return findUserByUsername(users, request.username())
                        .orElseThrow(
                                () -> new IllegalStateException("Keycloak reported a user conflict but the user was not found by username"))
                        .getId();
            }
            if (status != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException("Unexpected Keycloak user creation status: " + status);
            }
            final URI location = response.getLocation();
            if (location == null || !StringUtils.hasText(location.getPath())) {
                return findUserByUsername(users, request.username())
                        .orElseThrow(() -> new IllegalStateException("Keycloak user creation response did not include a Location header"))
                        .getId();
            }
            final String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private void updateUser(final UsersResource users, final String keycloakUserId, final TaalamKeycloakUserProvisioningRequest request) {
        users.get(keycloakUserId).update(userRepresentation(request));
    }

    private void sendRequiredActionsEmail(final UsersResource users, final String keycloakUserId) {
        final Provisioning provisioning = properties.getProvisioning();
        if (!provisioning.isSendActionsEmail() || provisioning.getRequiredActions().isEmpty()) {
            return;
        }

        users.get(keycloakUserId).executeActionsEmail(List.copyOf(provisioning.getRequiredActions()),
                Math.toIntExact(provisioning.getActionsEmailLifespanSeconds()));
    }

    private UserRepresentation userRepresentation(final TaalamKeycloakUserProvisioningRequest request) {
        final Provisioning provisioning = properties.getProvisioning();
        final UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(safeString(request.firstName()));
        user.setLastName(safeString(request.lastName()));
        user.setEnabled(provisioning.isUserEnabled());
        user.setEmailVerified(provisioning.isEmailVerified());
        user.setRequiredActions(List.copyOf(provisioning.getRequiredActions()));
        return user;
    }

    private String safeString(final String value) {
        return value == null ? "" : value;
    }
}
