package com.futbolix.courts.adapter.config;

import com.futbolix.courts.adapter.in.web.AdministratorTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Where the public keys of the administrator tokens come from (ftx-customers-api, ADR-012). */
@Configuration
public class AuthConfiguration {

    @Bean
    AdministratorTokenVerifier administratorTokenVerifier(@Value("${courts.auth.jwks-url}") String jwksUrl,
                                                          @Value("${courts.auth.issuer}") String issuer) {
        return AdministratorTokenVerifier.fromJwksUrl(jwksUrl, issuer);
    }
}
