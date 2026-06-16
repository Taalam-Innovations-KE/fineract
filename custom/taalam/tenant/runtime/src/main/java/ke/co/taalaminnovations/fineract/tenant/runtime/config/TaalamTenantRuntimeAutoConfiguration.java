/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan("ke.co.taalaminnovations.fineract.tenant.runtime")
@EnableConfigurationProperties(TaalamTenantRuntimeProperties.class)
@ConditionalOnProperty(prefix = "taalam.tenant.runtime", name = "enabled", havingValue = "true")
public class TaalamTenantRuntimeAutoConfiguration {}
