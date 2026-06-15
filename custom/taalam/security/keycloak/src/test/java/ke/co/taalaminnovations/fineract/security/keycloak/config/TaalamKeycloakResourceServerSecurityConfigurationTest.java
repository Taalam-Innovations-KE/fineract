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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakAuthenticationManagerResolver;
import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakIssuerTenantResolver;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreHelper;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.apache.fineract.infrastructure.jobs.filter.ProgressiveLoanModelCheckerFilter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class TaalamKeycloakResourceServerSecurityConfigurationTest {

    @Test
    void exposesPasswordEncoderRequiredByFineractUserServices() {
        TaalamKeycloakResourceServerSecurityConfiguration underTest = new TaalamKeycloakResourceServerSecurityConfiguration(
                mock(TaalamKeycloakAuthenticationManagerResolver.class), mock(TaalamKeycloakIssuerTenantResolver.class),
                mock(BusinessDateReadPlatformService.class), mock(MDCWrapper.class), mock(IdempotencyStoreHelper.class),
                mock(FineractRequestContextHolder.class), mock(FineractProperties.class), mock(ProgressiveLoanModelCheckerFilter.class));

        PasswordEncoder passwordEncoder = underTest.passwordEncoder();

        assertThat(passwordEncoder.matches("secret", passwordEncoder.encode("secret"))).isTrue();
    }
}
