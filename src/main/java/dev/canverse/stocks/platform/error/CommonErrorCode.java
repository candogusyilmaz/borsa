package dev.canverse.stocks.platform.error;

import java.util.Set;
import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested entity was not found.", "entity", "id"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    MISSING_REQUEST_VALUE(HttpStatus.BAD_REQUEST, "A required request value is missing.", "parameter"),
    REQUEST_BINDING_FAILED(HttpStatus.BAD_REQUEST, "The request could not be bound."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested response representation is not acceptable."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request payload is too large."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request media type is not supported."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable."),
    INVALID_STATUS(HttpStatus.CONFLICT, "The entity is not in the expected status.", "entity", "actual", "expected"),
    DUPLICATE_ENTITY(HttpStatus.CONFLICT, "The entity already exists.", "entity", "field", "value"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "The requested state change could not be applied."),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "The request contains invalid values.", "errors"),
    INACTIVE_RESOURCE(HttpStatus.UNPROCESSABLE_CONTENT, "The requested resource is inactive.", "entity"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred.", "detail");

    private final HttpStatus status;
    private final String description;
    private final Set<String> requiredParams;

    CommonErrorCode(HttpStatus status, String description, String... requiredParams) {
        this.status = status;
        this.description = description;
        this.requiredParams = Set.of(requiredParams);
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
        return requiredParams;
    }
}
