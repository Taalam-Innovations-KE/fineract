/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncUser;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeUserIdentityProvisioningResult;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeUserIdentityProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaalamKeycloakUserProvisioningService implements TenantRuntimeUserIdentityProvisioningService {

    private final TaalamKeycloakResourceServerProperties properties;
    private final TaalamKeycloakAdminClient adminClient;

    public TenantRuntimeUserIdentityProvisioningResult provisionUser(final TaalamKeycloakUserProvisioningRequest request) {
        if (!properties.getProvisioning().isEnabled()) {
            return TenantRuntimeUserIdentityProvisioningResult.skipped("Keycloak provisioning is disabled");
        }
        if (!StringUtils.hasText(request.tenantIdentifier())) {
            log.warn("Skipping Keycloak user provisioning for username [{}]: missing tenant identifier", request.username());
            return TenantRuntimeUserIdentityProvisioningResult.skipped("missing tenant identifier");
        }
        if (!StringUtils.hasText(request.username())) {
            log.warn("Skipping Keycloak user provisioning in tenant [{}]: missing username", request.tenantIdentifier());
            return TenantRuntimeUserIdentityProvisioningResult.skipped("missing username");
        }
        if (!StringUtils.hasText(request.email())) {
            log.warn("Skipping Keycloak user provisioning for username [{}] in tenant [{}]: missing email", request.username(),
                    request.tenantIdentifier());
            return TenantRuntimeUserIdentityProvisioningResult.skipped("missing email");
        }

        try {
            return adminClient.provisionUser(request);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("More than one Keycloak user matched")) {
                return new TenantRuntimeUserIdentityProvisioningResult("CONFLICT", e.getMessage());
            }
            return new TenantRuntimeUserIdentityProvisioningResult("FAILED", e.getMessage());
        }
    }

    @Override
    public TenantRuntimeUserIdentityProvisioningResult provisionUser(final String tenantIdentifier, final TenantRuntimeUserSyncUser user) {
        return provisionUser(new TaalamKeycloakUserProvisioningRequest(tenantIdentifier, user.username(), user.email(), user.firstName(),
                user.lastName()));
    }
}
