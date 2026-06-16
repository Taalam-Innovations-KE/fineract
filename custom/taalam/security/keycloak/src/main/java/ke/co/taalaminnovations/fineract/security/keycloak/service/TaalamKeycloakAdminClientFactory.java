/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.stereotype.Component;

@Component
public class TaalamKeycloakAdminClientFactory {

    Keycloak create(final String serverUrl, final String realm, final String clientId, final String clientSecret) {
        return KeycloakBuilder.builder().serverUrl(serverUrl).realm(realm).grantType("client_credentials").clientId(clientId)
                .clientSecret(clientSecret).build();
    }
}
