package com.futbolix.courts.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/v1/admin/**. The API Gateway validates the JWT and overwrites X-User-Id and
 * X-User-Role (07-api/authentication.md); this service is reachable only inside the platform
 * network, so it reads them and still refuses a request that lacks them or the right role.
 */
@Component
public class AdministratorInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    static final String ADMINISTRATOR_ID_ATTRIBUTE = "futbolix.administratorId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(USER_ID);
        String role = request.getHeader(USER_ROLE);
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        if (!ADMINISTRATOR.equals(role)) {
            throw new AdministratorRequiredException();
        }
        request.setAttribute(ADMINISTRATOR_ID_ATTRIBUTE, userId.strip());
        return true;
    }

    public static class AuthenticationRequiredException extends RuntimeException {
    }

    public static class AdministratorRequiredException extends RuntimeException {
    }
}
