package com.futbolix.courts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;

class AdministratorTokenVerifierTest {

    @Test
    void readsTheIdentityOfAValidToken() {
        assertThat(TestTokens.verifier().verify(TestTokens.administrator("admin-1")))
                .hasValueSatisfying(i -> {
                    assertThat(i.administratorId()).isEqualTo("admin-1");
                    assertThat(i.roles()).containsExactly("ADMINISTRATOR");
                });
    }

    @Test
    void refusesWhatItCannotTrust() {
        AdministratorTokenVerifier verifier = TestTokens.verifier();
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify(" ")).isEmpty();
        assertThat(verifier.verify("a.b.c")).isEmpty();
        assertThat(verifier.verify(TestTokens.expired("admin-1"))).isEmpty();
        assertThat(verifier.verify(TestTokens.fromIssuer("admin-1", "someone-else"))).isEmpty();
        assertThat(verifier.verify(TestTokens.signedByAnotherKey("admin-1"))).isEmpty();
    }

    /** Fail secure: without keys nothing gets through, and the caller can answer 503. */
    @Test
    void keysThatCannotBeFetchedAreReportedNotIgnored() {
        JWKSource<SecurityContext> down = (selector, context) -> {
            throw new KeySourceException("connection refused");
        };
        AdministratorTokenVerifier verifier = new AdministratorTokenVerifier(down, TestTokens.ISSUER);

        assertThatThrownBy(() -> verifier.verify(TestTokens.administrator("admin-1")))
                .isInstanceOf(AdministratorTokenVerifier.KeysUnavailableException.class);
    }

    @Test
    void aBadJwksUrlStopsTheStartUp() {
        assertThatThrownBy(() -> AdministratorTokenVerifier.fromJwksUrl("not a url", TestTokens.ISSUER))
                .isInstanceOf(IllegalStateException.class);
    }
}
