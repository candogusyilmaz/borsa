package dev.canverse.stocks.ledger.error;

import dev.canverse.stocks.platform.error.ErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;

public enum LedgerErrorCode implements ErrorCode {
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "The financial account was not found."),
    ACCOUNT_NAME_CONFLICT(HttpStatus.CONFLICT, "The financial account name is already in use."),
    ACCOUNT_VERSION_CONFLICT(HttpStatus.CONFLICT, "The financial account was changed by another request."),
    BALANCE_VERSION_CONFLICT(HttpStatus.CONFLICT, "The account balance was changed by another request."),
    ACCOUNT_ARCHIVED(HttpStatus.CONFLICT, "The financial account is archived."),
    ACCOUNT_ACTION_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "The requested account action is not supported."),
    ACCOUNT_CURRENCY_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "The requested currency is not available."),
    ACCOUNT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "The authorized account limit was exceeded."),
    FUTURE_TIME_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "Future effective or as-of ledger time is not allowed."),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "The account does not have sufficient funds."),
    POLICY_BREACH_CONFIRMATION_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT, "Explicit confirmation is required for this policy breach."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "The idempotency key was already used for another request."),
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "The activity was not found."),
    ACTIVITY_ALREADY_REVERSED(HttpStatus.CONFLICT, "The activity has already been reversed."),
    OPENING_STATE_CONFLICT(HttpStatus.CONFLICT, "The opening state could not be corrected."),
    RECONCILIATION_NOT_FOUND(HttpStatus.NOT_FOUND, "The reconciliation was not found."),
    RECONCILIATION_COVERAGE_GAP(
            HttpStatus.UNPROCESSABLE_CONTENT, "The statement opening is before the account's known coverage."),
    RECONCILIATION_OPENING_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT, "The statement opening does not match the ledger opening."),
    RECONCILIATION_RESOLUTION_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT, "The requested reconciliation resolution is not admissible."),
    RECONCILIATION_ALREADY_SUPERSEDED(HttpStatus.CONFLICT, "The reconciliation has already been superseded.");

    private final HttpStatus status;
    private final String description;

    LedgerErrorCode(HttpStatus status, String description) {
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
