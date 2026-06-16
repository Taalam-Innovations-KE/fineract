/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

public record TaalamKeycloakUserProvisioningRequest(String tenantIdentifier, String username, String email, String firstName,
        String lastName) {
}
