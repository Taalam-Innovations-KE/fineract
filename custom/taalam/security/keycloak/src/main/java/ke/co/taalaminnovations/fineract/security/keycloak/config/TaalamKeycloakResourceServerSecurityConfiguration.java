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

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import ke.co.taalaminnovations.fineract.security.keycloak.filter.TaalamKeycloakTenantFilter;
import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakAuthenticationManagerResolver;
import ke.co.taalaminnovations.fineract.security.keycloak.service.TaalamKeycloakIssuerTenantResolver;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.filters.CallerIpTrackingFilter;
import org.apache.fineract.infrastructure.core.filters.CorrelationHeaderFilter;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreFilter;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreHelper;
import org.apache.fineract.infrastructure.core.filters.RequestResponseFilter;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.apache.fineract.infrastructure.instancemode.filter.FineractInstanceModeApiFilter;
import org.apache.fineract.infrastructure.jobs.filter.LoanCOBApiFilter;
import org.apache.fineract.infrastructure.jobs.filter.LoanCOBFilterHelper;
import org.apache.fineract.infrastructure.jobs.filter.ProgressiveLoanModelCheckerFilter;
import org.apache.fineract.infrastructure.security.filter.BusinessDateFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class TaalamKeycloakResourceServerSecurityConfiguration {

    private final TaalamKeycloakAuthenticationManagerResolver keycloakAuthenticationManagerResolver;
    private final TaalamKeycloakIssuerTenantResolver tenantResolver;
    private final BusinessDateReadPlatformService businessDateReadPlatformService;
    private final MDCWrapper mdcWrapper;
    private final IdempotencyStoreHelper idempotencyStoreHelper;
    private final FineractRequestContextHolder fineractRequestContextHolder;
    private final FineractProperties fineractProperties;
    private final ProgressiveLoanModelCheckerFilter progressiveLoanModelCheckerFilter;

    @Autowired(required = false)
    private LoanCOBFilterHelper loanCOBFilterHelper;

    @Bean
    @Order(1)
    public SecurityFilterChain keycloakPublicEndpoints(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/fineract.json", "/actuator/**", "/legacy-docs/apiLive.htm")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS));

        if (fineractProperties.getSecurity().getCors().isEnabled()) {
            http.cors(cors -> cors.configurationSource(externalOauthCorsConfigurationSource()));
        }

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain keycloakProtectedEndpoints(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(
                        resourceServer -> resourceServer.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver()))
                .addFilterAfter(keycloakTenantFilter(), SecurityContextHolderFilter.class)
                .addFilterAfter(businessDateFilter(), TaalamKeycloakTenantFilter.class)
                .addFilterAfter(requestResponseFilter(), ExceptionTranslationFilter.class)
                .addFilterAfter(correlationHeaderFilter(), RequestResponseFilter.class)
                .addFilterAfter(fineractInstanceModeApiFilter(), CorrelationHeaderFilter.class);

        if (!Objects.isNull(loanCOBFilterHelper)) {
            http.addFilterAfter(loanCOBApiFilter(), FineractInstanceModeApiFilter.class).addFilterAfter(idempotencyStoreFilter(),
                    LoanCOBApiFilter.class);
            http.addFilterBefore(progressiveLoanModelCheckerFilter, LoanCOBApiFilter.class);
        } else {
            http.addFilterAfter(idempotencyStoreFilter(), FineractInstanceModeApiFilter.class);
            http.addFilterAfter(progressiveLoanModelCheckerFilter, FineractInstanceModeApiFilter.class);
        }

        if (fineractProperties.getIpTracking().isEnabled()) {
            http.addFilterAfter(callerIpTrackingFilter(), RequestResponseFilter.class);
        }
        if (fineractProperties.getSecurity().getCors().isEnabled()) {
            http.cors(cors -> cors.configurationSource(externalOauthCorsConfigurationSource()));
        }

        return http.build();
    }

    @Bean
    public BearerTokenResolver keycloakBearerTokenResolver() {
        return new DefaultBearerTokenResolver();
    }

    private AuthenticationManagerResolver<HttpServletRequest> jwtIssuerAuthenticationManagerResolver() {
        return new JwtIssuerAuthenticationManagerResolver(keycloakAuthenticationManagerResolver);
    }

    private TaalamKeycloakTenantFilter keycloakTenantFilter() {
        return new TaalamKeycloakTenantFilter(keycloakBearerTokenResolver(), tenantResolver);
    }

    private BusinessDateFilter businessDateFilter() {
        return new BusinessDateFilter(businessDateReadPlatformService);
    }

    private RequestResponseFilter requestResponseFilter() {
        return new RequestResponseFilter();
    }

    private LoanCOBApiFilter loanCOBApiFilter() {
        return new LoanCOBApiFilter(loanCOBFilterHelper);
    }

    private FineractInstanceModeApiFilter fineractInstanceModeApiFilter() {
        return new FineractInstanceModeApiFilter(fineractProperties);
    }

    private IdempotencyStoreFilter idempotencyStoreFilter() {
        return new IdempotencyStoreFilter(fineractRequestContextHolder, idempotencyStoreHelper, fineractProperties);
    }

    private CorrelationHeaderFilter correlationHeaderFilter() {
        return new CorrelationHeaderFilter(fineractProperties, mdcWrapper);
    }

    private CallerIpTrackingFilter callerIpTrackingFilter() {
        return new CallerIpTrackingFilter(fineractProperties);
    }

    private CorsConfigurationSource externalOauthCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        FineractProperties.CorsProperties corsConfiguration = fineractProperties.getSecurity().getCors();
        config.setAllowedOriginPatterns(corsConfiguration.getAllowedOriginPatterns());
        config.setAllowedMethods(corsConfiguration.getAllowedMethods());
        config.setAllowedHeaders(corsConfiguration.getAllowedHeaders());
        config.setExposedHeaders(corsConfiguration.getExposedHeaders());
        config.setAllowCredentials(corsConfiguration.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
