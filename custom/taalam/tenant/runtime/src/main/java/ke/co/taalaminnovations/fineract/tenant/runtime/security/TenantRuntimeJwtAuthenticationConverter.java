/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

public class TenantRuntimeJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    @Override
    public JwtAuthenticationToken convert(Jwt source) {
        return new JwtAuthenticationToken(source, authorities(source), principalName(source));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addScopeAuthorities(jwt, authorities);
        addRealmRoleAuthorities(jwt, authorities);
        addResourceRoleAuthorities(jwt, authorities);
        return authorities;
    }

    private void addScopeAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        Object scope = jwt.getClaims().getOrDefault("scope", jwt.getClaims().get("scp"));
        if (scope instanceof String value) {
            for (String item : value.split(" ")) {
                if (StringUtils.hasText(item)) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + item));
                    authorities.add(new SimpleGrantedAuthority(item));
                }
            }
        } else if (scope instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText)
                    .forEach(value -> {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
                        authorities.add(new SimpleGrantedAuthority(value));
                    });
        }
    }

    private void addRealmRoleAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            addRoleAuthorities(realmAccessMap.get("roles"), authorities);
        }
    }

    private void addResourceRoleAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
            for (Object clientAccess : resourceAccessMap.values()) {
                if (clientAccess instanceof Map<?, ?> clientAccessMap) {
                    addRoleAuthorities(clientAccessMap.get("roles"), authorities);
                }
            }
        }
    }

    private void addRoleAuthorities(Object roles, Set<GrantedAuthority> authorities) {
        if (roles instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new).forEach(authorities::add);
        }
    }

    private String principalName(Jwt jwt) {
        for (String claim : List.of("preferred_username", "azp", "client_id", "sub")) {
            String value = jwt.getClaimAsString(claim);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return jwt.getSubject();
    }
}
