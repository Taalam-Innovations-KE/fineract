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
