package dev.canverse.stocks.identity.error;

import dev.canverse.stocks.platform.error.ErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;

public enum IdentityErrorCode implements ErrorCode {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "The email address is already registered."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The provided credentials are invalid."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "The device session was not found."),
    AUTHENTICATION_THROTTLED(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts.");

    private final HttpStatus status;
    private final String description;

    IdentityErrorCode(HttpStatus status, String description) {
        this.status = status;
        this.description = description;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Set<String> getRequiredParams() {
        return Set.of();
    }
}
