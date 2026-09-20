package dev.canverse.stocks.investing.error;

import dev.canverse.stocks.platform.error.ErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;

public enum InvestingErrorCode implements ErrorCode {
    UNSUPPORTED_INSTRUMENT(HttpStatus.UNPROCESSABLE_CONTENT, "The instrument is not supported for this trade."),
    TRADE_CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "The account and instrument currencies must match."),
    INVALID_SETTLED_PRECISION(HttpStatus.UNPROCESSABLE_CONTENT, "The trade amount cannot be represented at the supported precision."),
    TRADE_PROCEEDS_NOT_POSITIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Sell proceeds after commission must be positive."),
    INSUFFICIENT_POSITION_QUANTITY(HttpStatus.UNPROCESSABLE_CONTENT, "The trade history would dispose more quantity than is held."),
    DUPLICATE_ECONOMIC_ORDER(HttpStatus.CONFLICT, "A trade already uses this account, instrument, effective time, and economic sequence."),
    POSITION_VERSION_CONFLICT(HttpStatus.CONFLICT, "The position was changed by another request."),
    PORTFOLIO_NAME_CONFLICT(HttpStatus.CONFLICT, "An active portfolio already uses this name."),
    PORTFOLIO_VERSION_CONFLICT(HttpStatus.CONFLICT, "The portfolio was changed by another request."),
    PORTFOLIO_ARCHIVED(HttpStatus.CONFLICT, "The portfolio is archived and cannot be changed."),
    IMPORT_NOT_COMMITTABLE(HttpStatus.CONFLICT, "The complete import batch cannot be committed in its current state."),
    IMPORT_PREVIEW_STALE(HttpStatus.CONFLICT, "The current import preview no longer matches the confirmed snapshot."),
    IMPORT_ALREADY_COMMITTED(HttpStatus.CONFLICT, "The import batch has already been committed."),
    IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "The import batch was not found."), PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "The portfolio was not found."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "The trade was not found."), POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "The position was not found."),
    IMPORT_FILE_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "The uploaded CSV must contain at least one data row."),
    IMPORT_FILE_ENCODING_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "The uploaded file must be valid UTF-8."),
    IMPORT_FILE_FORMAT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "The uploaded file does not match the supported CSV format."),
    IMPORT_ROW_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "The CSV contains more rows than the supported limit.");

    private final HttpStatus status;
    private final String description;

    InvestingErrorCode(HttpStatus status, String description) {
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
