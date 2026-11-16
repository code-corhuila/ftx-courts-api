package com.futbolix.courts.adapter.in.web;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the administrator access token itself instead of trusting X-User-Id / X-User-Role:
 * 07-api/authentication.md says a service must never trust client-supplied identity headers, and
 * this API also publishes its port on the host, so those headers can come straight from a client.
 *
 * The token must be RS256, signed by a key of the JWKS that ftx-customers-api publishes
 * (GET /api/v1/auth/jwks), issued by the configured issuer, not expired, and carry sub, iat, exp,
 * jti and roles (security-policy.md, "JWT").
 */
public class AdministratorTokenVerifier {

    private final DefaultJWTProcessor<SecurityContext> processor;

    public AdministratorTokenVerifier(JWKSource<SecurityContext> keys, String issuer) {
        processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuer).build(),
                Set.of("sub", "iat", "exp", "jti", "roles")));
    }

    /** Keys fetched from {@code jwksUrl}, cached and refreshed by Nimbus, 2 s timeouts. */
    public static AdministratorTokenVerifier fromJwksUrl(String jwksUrl, String issuer) {
        try {
            JWKSource<SecurityContext> keys = JWKSourceBuilder
                    .create(URI.create(jwksUrl).toURL(), new DefaultResourceRetriever(2000, 2000, 64 * 1024))
                    .build();
            return new AdministratorTokenVerifier(keys, issuer);
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("COURTS_AUTH_JWKS_URL is not a valid URL");
        }
    }

    /**
     * @return the identity of a valid token, or empty when the token is missing, malformed,
     *         expired, from another issuer or signed by an unknown key.
     * @throws KeysUnavailableException when the keys cannot be fetched: the caller answers 503,
     *         never lets the request through.
     */
    public Optional<Identity> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = processor.process(token, null);
            List<String> roles = claims.getStringListClaim("roles");
            return Optional.of(new Identity(claims.getSubject(), roles == null ? List.of() : List.copyOf(roles)));
        } catch (KeySourceException e) {
            throw new KeysUnavailableException(e);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            return Optional.empty();
        }
    }

    public record Identity(String administratorId, List<String> roles) {
    }

    public static class KeysUnavailableException extends RuntimeException {
        public KeysUnavailableException(Throwable cause) {
            super("The public keys of ftx-customers-api are unavailable", cause);
        }
    }
}
