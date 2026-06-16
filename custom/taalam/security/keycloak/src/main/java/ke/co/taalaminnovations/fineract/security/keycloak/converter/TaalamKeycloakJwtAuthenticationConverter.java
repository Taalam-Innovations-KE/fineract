/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.converter;

import java.util.ArrayList;
import java.util.Collection;
import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.data.FineractJwtAuthenticationToken;
import org.apache.fineract.infrastructure.security.domain.PlatformUser;
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
        ResolvedPrincipal principal = resolvePrincipal(jwt);
        if (isBlank(principal.username())) {
            throw invalidToken("Token does not identify a Fineract user");
        }

        try {
            UserDetails user = userDetailsService.loadUserByUsername(principal.username());
            validateEmailBinding(jwt, user, principal);
            Collection<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
            return new FineractJwtAuthenticationToken(jwt, authorities, user);
        } catch (UsernameNotFoundException ex) {
            throw invalidToken(ex);
        }
    }

    private ResolvedPrincipal resolvePrincipal(Jwt jwt) {
        String humanUsername = jwt.getClaimAsString(properties.getUsernameClaim());
        if (!isBlank(humanUsername)) {
            return new ResolvedPrincipal(humanUsername.trim(), false);
        }

        String serviceClientId = jwt.getClaimAsString(properties.getServiceClientClaim());
        if (!isBlank(serviceClientId)) {
            return new ResolvedPrincipal(properties.getServiceUserPrefix() + serviceClientId.trim(), true);
        }

        return new ResolvedPrincipal(null, false);
    }

    private void validateEmailBinding(Jwt jwt, UserDetails user, ResolvedPrincipal principal) {
        if (!properties.isRequireEmailMatch() || principal.serviceClient()) {
            return;
        }
        if (!(user instanceof PlatformUser platformUser)) {
            throw invalidToken("Resolved Fineract user does not expose an email address");
        }

        String tokenEmail = jwt.getClaimAsString(properties.getEmailClaim());
        if (isBlank(tokenEmail)) {
            throw invalidToken("Token does not identify a Fineract user email");
        }
        String fineractEmail = platformUser.getEmail();
        if (isBlank(fineractEmail) || !fineractEmail.trim().equalsIgnoreCase(tokenEmail.trim())) {
            throw invalidToken("Token email does not match the Fineract user email");
        }
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

    private record ResolvedPrincipal(String username, boolean serviceClient) {
    }
}
