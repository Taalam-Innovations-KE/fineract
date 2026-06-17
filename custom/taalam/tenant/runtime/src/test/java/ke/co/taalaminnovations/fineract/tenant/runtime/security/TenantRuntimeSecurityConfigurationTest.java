/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class TenantRuntimeSecurityConfigurationTest {

    @Test
    void jwtDecoderDiscoversAndVerifiesEdDsaJwk() throws Exception {
        OctetKeyPair signingKey = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID("ed-key")
                .algorithm(JWSAlgorithm.EdDSA)
                .generate();
        HttpServer jwkServer = jwkServer(signingKey);
        try {
            jwkServer.start();
            TaalamTenantRuntimeProperties properties = new TaalamTenantRuntimeProperties();
            properties.getOauth2().setJwkSetUri("http://localhost:" + jwkServer.getAddress().getPort() + "/certs");
            JwtDecoder decoder = new TenantRuntimeSecurityConfiguration(properties).tenantRuntimeJwtDecoder();

            Jwt jwt = decoder.decode(signedToken(signingKey, properties.getOauth2().getAudience()));

            assertThat(jwt.getSubject()).isEqualTo("tenant-management-service");
        } finally {
            jwkServer.stop(0);
        }
    }

    private HttpServer jwkServer(OctetKeyPair signingKey) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String body = new JWKSet(signingKey.toPublicJWK()).toString();
        server.createContext("/certs", exchange -> {
            byte[] response = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private String signedToken(OctetKeyPair signingKey, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("tenant-management-service")
                .audience(List.of(audience))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(), claims);
        jwt.sign(new Ed25519Signer(signingKey));
        return jwt.serialize();
    }
}
