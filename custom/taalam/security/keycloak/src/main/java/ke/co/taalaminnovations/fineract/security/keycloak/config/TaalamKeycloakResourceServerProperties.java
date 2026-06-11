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
package ke.co.taalaminnovations.fineract.security.keycloak.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fineract.security.oauth2.external")
@Getter
@Setter
public class TaalamKeycloakResourceServerProperties {

    private boolean enabled;
    private String keycloakBaseUrl;
    private String audience = "fineract";
    private String usernameClaim = "preferred_username";
    private String serviceClientClaim = "azp";
    private String serviceUserPrefix = "svc-";
    private Provisioning provisioning = new Provisioning();

    public String normalizedKeycloakBaseUrl() {
        if (keycloakBaseUrl == null) {
            return null;
        }
        return keycloakBaseUrl.endsWith("/") ? keycloakBaseUrl.substring(0, keycloakBaseUrl.length() - 1) : keycloakBaseUrl;
    }

    @Getter
    @Setter
    public static final class Provisioning {

        private boolean enabled;
        private String adminRealm = "master";
        private String adminClientId;
        private String adminClientSecret;
        private boolean userEnabled = true;
        private boolean emailVerified;
        private boolean sendActionsEmail = true;
        private long actionsEmailLifespanSeconds = 43_200L;
        private boolean syncUsernameOnEmailMatch = true;
        private List<String> requiredActions = List.of("UPDATE_PASSWORD");
    }
}
