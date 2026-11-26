package dev.canverse.stocks.rest;

import dev.canverse.stocks.domain.exception.ApiException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var errors = getValidationErrors(ex);
        log.atDebug()
                .addKeyValue("event", "validation_failed")
                .addKeyValue("fieldErrorCount", errors.size())
                .log("Request validation failed");
        ex.getBody().setProperty("invalid-params", errors);
        return ResponseEntity.status(status).body(ex.getBody());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        IllegalStateException.class,
        NullPointerException.class,
        IndexOutOfBoundsException.class
    })
    public ResponseEntity<Object> handlePreconditions(
            RuntimeException ex, ServletWebRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setInstance(URI.create(request.getRequest().getRequestURI()));

        if (ex instanceof NullPointerException || ex instanceof IndexOutOfBoundsException) {
            log.atError()
                    .addKeyValue("event", "unhandled_exception")
                    .addKeyValue("category", "application")
                    .addKeyValue("exception", ex.getClass().getSimpleName())
                    .setCause(ex)
                    .log("Unexpected server error");
        } else {
            log.atWarn()
                    .addKeyValue("event", "business_rule_violation")
                    .addKeyValue("exception", ex.getClass().getSimpleName())
                    .log("Request rejected by business rule");
        }

        return ResponseEntity.status(detail.getStatus()).body(detail);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex, ServletWebRequest request) {
        var detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        detail.setTitle(ex.getClass().getSimpleName());
        detail.setInstance(URI.create(request.getRequest().getRequestURI()));

        String category = ex instanceof DataAccessException ? "database" : "application";
        log.atError()
                .addKeyValue("event", "unhandled_exception")
                .addKeyValue("category", category)
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .setCause(ex)
                .log("Unhandled server exception");

        return ResponseEntity.status(detail.getStatus()).body(detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException() {
        log.atWarn().addKeyValue("event", "authorization_failed").log("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(
            BadCredentialsException ex, ServletWebRequest request) {
        log.atWarn()
                .addKeyValue("event", "authentication_failed")
                .addKeyValue("authMethod", "password")
                .addKeyValue("reason", "bad_credentials")
                .log("Authentication failed");

        var detail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        detail.setInstance(URI.create(request.getRequest().getRequestURI()));

        return ResponseEntity.status(detail.getStatus()).body(detail);
    }

    @ExceptionHandler({JwtException.class, UsernameNotFoundException.class})
    public ResponseEntity<Object> handleInvalidTokenException(
            RuntimeException ex, ServletWebRequest request) {
        log.atWarn()
                .addKeyValue("event", "authentication_failed")
                .addKeyValue("reason", "invalid_token")
                .addKeyValue("exception", ex.getClass().getSimpleName())
                .log("Token authentication failed");

        var detail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
        detail.setInstance(URI.create(request.getRequest().getRequestURI()));

        return ResponseEntity.status(detail.getStatus()).body(detail);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, ServletWebRequest request) {
        if (ex.getHttpStatusCode().is5xxServerError()) {
            log.atError()
                    .addKeyValue("event", "api_error")
                    .addKeyValue("status", ex.getHttpStatusCode().value())
                    .addKeyValue("exception", ex.getClass().getSimpleName())
                    .setCause(ex)
                    .log("Server API exception");
        } else {
            log.atDebug()
                    .addKeyValue("event", "api_error")
                    .addKeyValue("status", ex.getHttpStatusCode().value())
                    .addKeyValue("exception", ex.getClass().getSimpleName())
                    .log("Client API exception");
        }

        ex.getBody().setInstance(URI.create(request.getRequest().getRequestURI()));
        return ResponseEntity.status(ex.getHttpStatusCode()).body(ex.getBody());
    }

    private static List<InvalidParameter> getValidationErrors(MethodArgumentNotValidException ex) {
        var result = new ArrayList<InvalidParameter>();

        ex.getFieldErrors()
                .forEach(
                        error -> {
                            var field = error.getField();
                            var message =
                                    error.getDefaultMessage() != null
                                            ? error.getDefaultMessage()
                                            : "";
                            var code = error.getCode();

                            result.add(new InvalidParameter(field, message, code));
                        });

        return result;
    }

    record InvalidParameter(String field, String message, String code) {}
}
