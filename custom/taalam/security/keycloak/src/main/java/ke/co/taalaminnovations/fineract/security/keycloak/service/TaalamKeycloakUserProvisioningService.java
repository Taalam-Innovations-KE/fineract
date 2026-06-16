/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaalamKeycloakUserProvisioningService {

    private final TaalamKeycloakResourceServerProperties properties;
    private final TaalamKeycloakAdminClient adminClient;

    public void provisionUser(final TaalamKeycloakUserProvisioningRequest request) {
        if (!properties.getProvisioning().isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(request.tenantIdentifier())) {
            log.warn("Skipping Keycloak user provisioning for username [{}]: missing tenant identifier", request.username());
            return;
        }
        if (!StringUtils.hasText(request.username())) {
            log.warn("Skipping Keycloak user provisioning in tenant [{}]: missing username", request.tenantIdentifier());
            return;
        }
        if (!StringUtils.hasText(request.email())) {
            log.warn("Skipping Keycloak user provisioning for username [{}] in tenant [{}]: missing email", request.username(),
                    request.tenantIdentifier());
            return;
        }

        adminClient.provisionUser(request);
    }
}
