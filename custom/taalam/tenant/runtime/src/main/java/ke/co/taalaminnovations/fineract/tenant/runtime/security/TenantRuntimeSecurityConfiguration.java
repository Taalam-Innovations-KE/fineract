/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
public class TenantRuntimeSecurityConfiguration {

    private static final PathPatternRequestMatcher.Builder API_MATCHER = PathPatternRequestMatcher.withDefaults();

    private final TaalamTenantRuntimeProperties properties;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain tenantRuntimeSecurityFilterChain(HttpSecurity http, JwtDecoder tenantRuntimeJwtDecoder) throws Exception {
        http.securityMatcher(API_MATCHER.matcher("/api/*/tenant-runtime/**")).csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(requiredAuthority()))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.decoder(tenantRuntimeJwtDecoder).jwtAuthenticationConverter(new TenantRuntimeJwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder tenantRuntimeJwtDecoder() {
        TaalamTenantRuntimeProperties.OAuth2 oauth2 = properties.getOauth2();
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(oauth2.getJwkSetUri())) {
            decoder = NimbusJwtDecoder.withJwkSetUri(oauth2.getJwkSetUri())
                    .jwtProcessorCustomizer(TenantRuntimeSecurityConfiguration::discoverJwsAlgorithms)
                    .build();
        } else if (StringUtils.hasText(oauth2.getIssuerUri())) {
            decoder = NimbusJwtDecoder.withIssuerLocation(oauth2.getIssuerUri())
                    .jwtProcessorCustomizer(TenantRuntimeSecurityConfiguration::discoverJwsAlgorithms)
                    .build();
        } else {
            throw new IllegalStateException(
                    "taalam.tenant.runtime.oauth2.issuer-uri or jwk-set-uri is required when tenant runtime is enabled");
        }

        if (StringUtils.hasText(oauth2.getIssuerUri())) {
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                    JwtValidators.createDefaultWithIssuer(oauth2.getIssuerUri()), new JwtAudienceValidator(oauth2.getAudience())));
        } else {
            decoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<Jwt>(JwtValidators.createDefault(), new JwtAudienceValidator(oauth2.getAudience())));
        }
        return decoder;
    }

    private static void discoverJwsAlgorithms(ConfigurableJWTProcessor<SecurityContext> processor) {
        JWSKeySelector<SecurityContext> keySelector = processor.getJWSKeySelector();
        if (keySelector instanceof JWSVerificationKeySelector<?> verificationKeySelector) {
            @SuppressWarnings("unchecked")
            JWKSource<SecurityContext> jwkSource = (JWKSource<SecurityContext>) verificationKeySelector.getJWKSource();
            processor.setJWSKeySelector(new KeycloakJwsKeySelector(jwsAlgorithms(jwkSource), jwkSource));
            processor.setJWSVerifierFactory(new KeycloakJwsVerifierFactory());
        }
    }

    private static Set<JWSAlgorithm> jwsAlgorithms(JWKSource<SecurityContext> jwkSource) {
        JWKMatcher matcher = new JWKMatcher.Builder()
                .publicOnly(true)
                .keyUses(KeyUse.SIGNATURE, null)
                .keyTypes(KeyType.RSA, KeyType.EC, KeyType.OKP)
                .build();
        Set<JWSAlgorithm> algorithms = new LinkedHashSet<>();
        try {
            List<? extends JWK> jwks = jwkSource.get(new JWKSelector(matcher), null);
            for (JWK jwk : jwks) {
                addAlgorithms(jwk, algorithms);
            }
        } catch (KeySourceException ex) {
            throw new IllegalStateException("Unable to discover JWS algorithms from JWK set", ex);
        }
        if (algorithms.isEmpty()) {
            throw new IllegalStateException("Failed to find any signing algorithms from the JWK set");
        }
        return algorithms;
    }

    private static void addAlgorithms(JWK jwk, Set<JWSAlgorithm> algorithms) {
        if (jwk.getAlgorithm() != null) {
            algorithms.add(JWSAlgorithm.parse(jwk.getAlgorithm().getName()));
            return;
        }
        if (jwk.getKeyType() == KeyType.RSA) {
            algorithms.addAll(new HashSet<>(JWSAlgorithm.Family.RSA));
        } else if (jwk.getKeyType() == KeyType.EC) {
            algorithms.addAll(new HashSet<>(JWSAlgorithm.Family.EC));
        } else if (jwk.getKeyType() == KeyType.OKP) {
            algorithms.add(JWSAlgorithm.EdDSA);
        }
    }

    private static final class KeycloakJwsKeySelector implements JWSKeySelector<SecurityContext> {

        private final Set<JWSAlgorithm> algorithms;
        private final JWKSource<SecurityContext> jwkSource;
        private final JWSVerificationKeySelector<SecurityContext> delegate;

        private KeycloakJwsKeySelector(Set<JWSAlgorithm> algorithms, JWKSource<SecurityContext> jwkSource) {
            this.algorithms = algorithms;
            this.jwkSource = jwkSource;
            this.delegate = new JWSVerificationKeySelector<>(algorithms, jwkSource);
        }

        @Override
        public List<Key> selectJWSKeys(JWSHeader jwsHeader, SecurityContext context) throws KeySourceException {
            if (!algorithms.contains(jwsHeader.getAlgorithm())) {
                return Collections.emptyList();
            }
            if (JWSAlgorithm.EdDSA.equals(jwsHeader.getAlgorithm())) {
                return edDsaKeys(jwsHeader, context);
            }
            return delegate.selectJWSKeys(jwsHeader, context);
        }

        private List<Key> edDsaKeys(JWSHeader jwsHeader, SecurityContext context) throws KeySourceException {
            JWKMatcher matcher = JWKMatcher.forJWSHeader(jwsHeader);
            List<JWK> jwks = jwkSource.get(new JWKSelector(matcher), context);
            List<Key> keys = new ArrayList<>();
            for (JWK jwk : jwks) {
                if (jwk instanceof OctetKeyPair octetKeyPair && !octetKeyPair.isPrivate()) {
                    keys.add(new OctetKeyPairPublicKey(octetKeyPair));
                }
            }
            return keys;
        }
    }

    private static final class KeycloakJwsVerifierFactory extends DefaultJWSVerifierFactory {

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            Set<JWSAlgorithm> algorithms = new LinkedHashSet<>(super.supportedJWSAlgorithms());
            algorithms.add(JWSAlgorithm.EdDSA);
            return Collections.unmodifiableSet(algorithms);
        }

        @Override
        public JWSVerifier createJWSVerifier(JWSHeader header, Key key) throws JOSEException {
            if (JWSAlgorithm.EdDSA.equals(header.getAlgorithm()) && key instanceof OctetKeyPairPublicKey publicKey) {
                return new Ed25519Verifier(publicKey.octetKeyPair());
            }
            return super.createJWSVerifier(header, key);
        }
    }

    private record OctetKeyPairPublicKey(OctetKeyPair octetKeyPair) implements Key {

        @Override
        public String getAlgorithm() {
            return JWSAlgorithm.EdDSA.getName();
        }

        @Override
        public String getFormat() {
            return "JWK";
        }

        @Override
        public byte[] getEncoded() {
            return octetKeyPair.toJSONString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private String requiredAuthority() {
        String configuredAuthority = properties.getOauth2().getRequiredAuthority();
        return StringUtils.hasText(configuredAuthority) ? configuredAuthority : "TENANT_RUNTIME_REGISTER";
    }
}
