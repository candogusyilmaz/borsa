package dev.canverse.stocks.reference.error;

import dev.canverse.stocks.platform.error.ErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;

public enum ReferenceErrorCode implements ErrorCode {
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested market was not found."),
    CURRENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested currency was not found."),
    INSTRUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested instrument was not found."),
    DUPLICATE_INSTRUMENT(HttpStatus.CONFLICT, "The instrument already exists for this owner and market."),
    DUPLICATE_INSTRUMENT_ALIAS(HttpStatus.CONFLICT, "The instrument alias already exists."),
    INSTRUMENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "The instrument was changed by another request."),
    INACTIVE_REFERENCE(HttpStatus.UNPROCESSABLE_CONTENT, "The requested reference row is inactive."),
    UNSUPPORTED_MARKET_CURRENCY(HttpStatus.UNPROCESSABLE_CONTENT, "The currency is not supported by the market.");

    private final HttpStatus status;
    private final String description;

    ReferenceErrorCode(HttpStatus status, String description) {
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
