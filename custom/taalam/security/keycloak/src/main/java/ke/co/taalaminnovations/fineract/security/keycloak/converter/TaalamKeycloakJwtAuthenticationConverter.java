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

import java.util.ArrayList;
import java.util.Collection;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.data.FineractJwtAuthenticationToken;
import org.apache.fineract.infrastructure.security.service.TenantAwareJpaPlatformUserDetailsService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaalamKeycloakJwtAuthenticationConverter implements Converter<Jwt, FineractJwtAuthenticationToken> {

    private final TenantAwareJpaPlatformUserDetailsService userDetailsService;
    private final TaalamKeycloakResourceServerProperties properties;

    @Override
    @NonNull
    public FineractJwtAuthenticationToken convert(@NonNull Jwt jwt) {
        String username = resolveUsername(jwt);
        if (isBlank(username)) {
            throw invalidToken("Token does not identify a Fineract user");
        }

        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            Collection<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
            return new FineractJwtAuthenticationToken(jwt, authorities, user);
        } catch (UsernameNotFoundException ex) {
            throw invalidToken(ex);
        }
    }

    private String resolveUsername(Jwt jwt) {
        String humanUsername = jwt.getClaimAsString(properties.getUsernameClaim());
        if (!isBlank(humanUsername)) {
            return humanUsername;
        }

        String serviceClientId = jwt.getClaimAsString(properties.getServiceClientClaim());
        if (!isBlank(serviceClientId)) {
            return properties.getServiceUserPrefix() + serviceClientId;
        }

        return jwt.getSubject();
    }

    private OAuth2AuthenticationException invalidToken(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, message, null));
    }

    private OAuth2AuthenticationException invalidToken(UsernameNotFoundException ex) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN), ex);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
