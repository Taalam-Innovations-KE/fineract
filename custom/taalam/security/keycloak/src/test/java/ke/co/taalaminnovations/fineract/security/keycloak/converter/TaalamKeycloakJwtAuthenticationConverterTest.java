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
package ke.co.taalaminnovations.fineract.security.keycloak.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import org.apache.fineract.infrastructure.security.data.FineractJwtAuthenticationToken;
import org.apache.fineract.infrastructure.security.service.TenantAwareJpaPlatformUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class TaalamKeycloakJwtAuthenticationConverterTest {

    private final TenantAwareJpaPlatformUserDetailsService userDetailsService = mock(TenantAwareJpaPlatformUserDetailsService.class);
    private final TaalamKeycloakResourceServerProperties properties = new TaalamKeycloakResourceServerProperties();
    private TaalamKeycloakJwtAuthenticationConverter underTest;

    @BeforeEach
    void setUp() {
        underTest = new TaalamKeycloakJwtAuthenticationConverter(userDetailsService, properties);
    }

    @Test
    void mapsHumanTokenByPreferredUsernameAndUsesAppUserAuthorities() {
        UserDetails user = User.withUsername("jane").password("unused").authorities("READ_LOAN").build();
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        Jwt jwt = jwtBuilder().claim("preferred_username", "jane").subject("keycloak-id").build();

        FineractJwtAuthenticationToken authentication = underTest.convert(jwt);

        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("READ_LOAN");
    }

    @Test
    void mapsServiceTokenByAuthorizedPartyToServiceUser() {
        UserDetails user = User.withUsername("svc-customer-channel").password("unused").authorities("READ_CLIENT").build();
        when(userDetailsService.loadUserByUsername("svc-customer-channel")).thenReturn(user);
        Jwt jwt = jwtBuilder().claim("azp", "customer-channel").subject("service-account-customer-channel").build();

        FineractJwtAuthenticationToken authentication = underTest.convert(jwt);

        assertThat(authentication.getPrincipal()).isSameAs(user);
    }

    @Test
    void fallsBackToSubjectWhenUsernameAndServiceClientClaimsAreAbsent() {
        UserDetails user = User.withUsername("legacy-subject").password("unused").authorities("READ_CLIENT").build();
        when(userDetailsService.loadUserByUsername("legacy-subject")).thenReturn(user);
        Jwt jwt = jwtBuilder().subject("legacy-subject").build();

        FineractJwtAuthenticationToken authentication = underTest.convert(jwt);

        assertThat(authentication.getPrincipal()).isSameAs(user);
    }

    @Test
    void rejectsUnknownFineractUser() {
        when(userDetailsService.loadUserByUsername("missing")).thenThrow(new UsernameNotFoundException("missing"));
        Jwt jwt = jwtBuilder().claim("preferred_username", "missing").build();

        assertThatThrownBy(() -> underTest.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt.Builder jwtBuilder() {
        Instant issuedAt = Instant.parse("2026-06-10T00:00:00Z");
        return Jwt.withTokenValue("token").header("alg", "RS256").issuer("https://id.example.org/realms/default")
                .audience(List.of("fineract")).issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(300));
    }
}
