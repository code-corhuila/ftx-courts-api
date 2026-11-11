package com.futbolix.courts.adapter.in.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Web slice tests: the administrator token verifier trusts only the key of {@link TestTokens}. */
@TestConfiguration
public class TestVerifierConfiguration {

    @Bean
    AdministratorTokenVerifier administratorTokenVerifier() {
        return TestTokens.verifier();
    }
}
