package com.futbolix.courts.adapter.in.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Tokens shaped like the ones ftx-customers-api issues (RS256, iss, sub, iat, exp, jti, roles),
 * signed with a key made for this test run, and a verifier that trusts only that key.
 */
public final class TestTokens {

    public static final String ISSUER = "ftx-customers-api";
    private static final RSAKey KEY = newKey("test-key");

    private TestTokens() {
    }

    public static AdministratorTokenVerifier verifier() {
        return new AdministratorTokenVerifier(new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())), ISSUER);
    }

    public static String administrator(String id) {
        return sign(KEY, ISSUER, id, List.of("ADMINISTRATOR"), Instant.now(), Duration.ofHours(1));
    }

    public static String withRole(String id, String role) {
        return sign(KEY, ISSUER, id, List.of(role), Instant.now(), Duration.ofHours(1));
    }

    public static String expired(String id) {
        return sign(KEY, ISSUER, id, List.of("ADMINISTRATOR"), Instant.now().minus(Duration.ofHours(2)), Duration.ofHours(1));
    }

    public static String fromIssuer(String id, String issuer) {
        return sign(KEY, issuer, id, List.of("ADMINISTRATOR"), Instant.now(), Duration.ofHours(1));
    }

    /** Same kid, but a key the verifier does not know. */
    public static String signedByAnotherKey(String id) {
        return sign(newKey("test-key"), ISSUER, id, List.of("ADMINISTRATOR"), Instant.now(), Duration.ofHours(1));
    }

    static String sign(RSAKey key, String issuer, String subject, List<String> roles, Instant issuedAt, Duration lifetime) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).subject(subject).jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt)).expirationTime(Date.from(issuedAt.plus(lifetime)))
                .claim("roles", roles).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static RSAKey newKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).keyUse(KeyUse.SIGNATURE).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
