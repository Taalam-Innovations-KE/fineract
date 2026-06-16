/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
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
    private String emailClaim = "email";
    private boolean requireEmailMatch = true;
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
        private UiClient uiClient = new UiClient();
    }

    @Getter
    @Setter
    public static final class UiClient {

        private String clientId = "fineract-ui";
        private String baseUrl;
    }
}
