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
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "The portfolio was not found."), TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "The trade was not found."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "The position was not found.");

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
