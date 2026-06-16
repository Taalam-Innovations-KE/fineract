/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import ke.co.taalaminnovations.fineract.security.keycloak.converter.TaalamKeycloakJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaalamKeycloakAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

    private final TaalamKeycloakIssuerTenantResolver tenantResolver;
    private final TaalamKeycloakJwtAuthenticationConverter authenticationConverter;
    private final TaalamKeycloakResourceServerProperties properties;
    private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();

    @Override
    public AuthenticationManager resolve(String issuer) {
        tenantResolver.resolveTenant(issuer);
        return authenticationManagers.computeIfAbsent(issuer, this::authenticationManager);
    }

    int cachedAuthenticationManagerCount() {
        return authenticationManagers.size();
    }

    private AuthenticationManager authenticationManager(String issuer) {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuer);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtAudienceValidator(properties.getAudience())));

        JwtAuthenticationProvider authenticationProvider = new JwtAuthenticationProvider(jwtDecoder);
        authenticationProvider.setJwtAuthenticationConverter(authenticationConverter);
        return authenticationProvider::authenticate;
    }
}
