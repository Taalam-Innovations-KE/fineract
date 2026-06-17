/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.config;

import org.apache.fineract.useradministration.starter.UserAdministrationConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration(before = UserAdministrationConfiguration.class)
@ComponentScan("ke.co.taalaminnovations.fineract.security.keycloak")
@EnableConfigurationProperties(TaalamKeycloakResourceServerProperties.class)
public class TaalamKeycloakResourceServerAutoConfiguration {}
