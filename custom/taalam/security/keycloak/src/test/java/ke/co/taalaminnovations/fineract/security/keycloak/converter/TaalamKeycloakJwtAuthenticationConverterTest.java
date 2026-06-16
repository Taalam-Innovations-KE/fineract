/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import org.apache.fineract.infrastructure.security.data.FineractJwtAuthenticationToken;
import org.apache.fineract.infrastructure.security.domain.PlatformUser;
import org.apache.fineract.infrastructure.security.service.TenantAwareJpaPlatformUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        PlatformUser user = mock(PlatformUser.class);
        when(user.getEmail()).thenReturn("jane@example.org");
        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("READ_LOAN"));
        doReturn(authorities).when(user).getAuthorities();
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        Jwt jwt = jwtBuilder().claim("preferred_username", "jane").claim("email", "jane@example.org").subject("keycloak-id").build();

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
    void rejectsTokenWhenUsernameAndServiceClientClaimsAreAbsent() {
        Jwt jwt = jwtBuilder().subject("legacy-subject").build();

        assertThatThrownBy(() -> underTest.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void rejectsHumanTokenWithoutEmailClaim() {
        PlatformUser user = mock(PlatformUser.class);
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        Jwt jwt = jwtBuilder().claim("preferred_username", "jane").build();

        assertThatThrownBy(() -> underTest.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void rejectsHumanTokenWhenEmailDoesNotMatchFineractUser() {
        PlatformUser user = mock(PlatformUser.class);
        when(user.getEmail()).thenReturn("jane@example.org");
        when(userDetailsService.loadUserByUsername("jane")).thenReturn(user);
        Jwt jwt = jwtBuilder().claim("preferred_username", "jane").claim("email", "wrong@example.org").build();

        assertThatThrownBy(() -> underTest.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void rejectsUnknownFineractUser() {
        when(userDetailsService.loadUserByUsername("missing")).thenThrow(new UsernameNotFoundException("missing"));
        Jwt jwt = jwtBuilder().claim("preferred_username", "missing").claim("email", "missing@example.org").build();

        assertThatThrownBy(() -> underTest.convert(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt.Builder jwtBuilder() {
        Instant issuedAt = Instant.parse("2026-06-10T00:00:00Z");
        return Jwt.withTokenValue("token").header("alg", "RS256").issuer("https://id.example.org/realms/default")
                .audience(List.of("fineract")).issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(300));
    }
}
