package com.futbolix.courts.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/v1/admin/**. The identity comes only from the Bearer access token, checked by
 * {@link AdministratorTokenVerifier}. X-User-Id and X-User-Role are ignored: this API publishes its
 * port on the host, so a client can send them, and 07-api/authentication.md says "Upstream services
 * must never trust client-supplied values for X-User-Id or X-User-Role". The gateway forwards the
 * Authorization header unchanged.
 */
@Component
public class AdministratorInterceptor implements HandlerInterceptor {

    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    static final String ADMINISTRATOR_ID_ATTRIBUTE = "futbolix.administratorId";
    private static final String BEARER = "Bearer ";

    private final AdministratorTokenVerifier verifier;

    public AdministratorInterceptor(AdministratorTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith(BEARER)
                ? authorization.substring(BEARER.length()).strip() : null;
        AdministratorTokenVerifier.Identity identity = verifier.verify(token)
                .orElseThrow(AuthenticationRequiredException::new);
        if (!identity.roles().contains(ADMINISTRATOR)) {
            throw new AdministratorRequiredException();
        }
        request.setAttribute(ADMINISTRATOR_ID_ATTRIBUTE, identity.administratorId());
        return true;
    }

    public static class AuthenticationRequiredException extends RuntimeException {
    }

    public static class AdministratorRequiredException extends RuntimeException {
    }
}
