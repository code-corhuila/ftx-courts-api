package com.futbolix.courts.adapter.in.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.futbolix.courts.application.CourtNameTakenException;
import com.futbolix.courts.application.CourtNotFoundException;
import com.futbolix.courts.application.EmptyCourtUpdateException;
import com.futbolix.courts.domain.InvalidCourtException;
import com.futbolix.courts.domain.InvalidCourtStatusTransitionException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to the ErrorResponse of _shared.yaml. Never leaks internals (A05/A09). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidCourtException.class)
    ResponseEntity<ErrorResponse> invalidCourt(InvalidCourtException e) {
        return validation(List.of(new ErrorResponse.Detail(e.field(), e.getMessage())));
    }

    @ExceptionHandler(EmptyCourtUpdateException.class)
    ResponseEntity<ErrorResponse> emptyUpdate(EmptyCourtUpdateException e) {
        return validation(List.of(new ErrorResponse.Detail(null, e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> badPathValue(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new ErrorResponse.Detail(e.getName(), "Invalid value")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return validation(List.of(new ErrorResponse.Detail(path(unknown), "Unknown field")));
        }
        if (cause instanceof InvalidFormatException invalid) {
            return validation(List.of(new ErrorResponse.Detail(path(invalid), "Invalid value")));
        }
        return validation(List.of(new ErrorResponse.Detail(null, "The body must be a JSON object")));
    }

    @ExceptionHandler(AdministratorInterceptor.AuthenticationRequiredException.class)
    ResponseEntity<ErrorResponse> unauthenticated() {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required", List.of());
    }

    @ExceptionHandler(AdministratorInterceptor.AdministratorRequiredException.class)
    ResponseEntity<ErrorResponse> forbidden() {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Administrator authorization is required", List.of());
    }

    /** The keys to check the token cannot be fetched: refuse, never let the request through. */
    @ExceptionHandler(AdministratorTokenVerifier.KeysUnavailableException.class)
    ResponseEntity<ErrorResponse> keysUnavailable(AdministratorTokenVerifier.KeysUnavailableException e) {
        log.warn("Administrator token keys unavailable: {}", e.getCause() == null ? "-" : e.getCause().getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "The service is temporarily unavailable", List.of());
    }

    @ExceptionHandler({CourtNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> notFound() {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found", List.of());
    }

    @ExceptionHandler(CourtNameTakenException.class)
    ResponseEntity<ErrorResponse> nameTaken() {
        return error(HttpStatus.CONFLICT, "CONFLICT", "Another court already uses this name",
                List.of(new ErrorResponse.Detail("name", "The name is already in use")));
    }

    @ExceptionHandler(InvalidCourtStatusTransitionException.class)
    ResponseEntity<ErrorResponse> invalidTransition(InvalidCourtStatusTransitionException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COURT_STATUS_TRANSITION",
                "The requested status transition is not allowed",
                List.of(new ErrorResponse.Detail("status", e.getMessage())));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed() {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The method is not allowed on this resource", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send the body as application/json", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected internal server error occurred", List.of());
    }

    private static ResponseEntity<ErrorResponse> validation(List<ErrorResponse.Detail> details) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request data", details);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                                       List<ErrorResponse.Detail> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, details, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private static String path(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
