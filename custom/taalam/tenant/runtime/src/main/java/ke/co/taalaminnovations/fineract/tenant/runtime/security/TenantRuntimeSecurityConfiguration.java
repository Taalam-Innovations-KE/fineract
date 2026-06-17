/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.util.ArrayList;
import java.util.List;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class TenantRuntimeSecurityConfiguration {

    private static final PathPatternRequestMatcher.Builder API_MATCHER = PathPatternRequestMatcher.withDefaults();

    private final TaalamTenantRuntimeProperties properties;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain tenantRuntimeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(API_MATCHER.matcher("/api/*/tenant-runtime/**")).csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS));

        if (isJwtEnabled()) {
            // Keycloak-issued JWT (client-credentials) authentication.
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        } else {
            // Backward-compatible shared-secret header authentication.
            http.addFilterBefore(new SharedSecretAuthFilter(properties), SecurityContextHolderFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    private boolean isJwtEnabled() {
        return StringUtils.hasText(properties.getJwkSetUri()) || StringUtils.hasText(properties.getIssuerUri());
    }

    private NimbusJwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = StringUtils.hasText(properties.getJwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build()
                : NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri()).build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(StringUtils.hasText(properties.getIssuerUri())
                ? JwtValidators.createDefaultWithIssuer(properties.getIssuerUri())
                : JwtValidators.createDefault());
        if (StringUtils.hasText(properties.getAudience())) {
            final String audience = properties.getAudience();
            validators.add(token -> token.getAudience() != null && token.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Required audience '" + audience + "' is missing", null)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
