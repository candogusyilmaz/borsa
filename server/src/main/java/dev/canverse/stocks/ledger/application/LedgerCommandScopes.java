package dev.canverse.stocks.ledger.application;

/** Stable idempotency namespaces for ledger commands. */
final class LedgerCommandScopes {

    static final String ACCOUNT_CREATE = "ledger.account.create";
    static final String ACCOUNT_METADATA = "ledger.account.metadata";
    static final String ACCOUNT_POLICY = "ledger.account.policy";
    static final String ACCOUNT_ARCHIVE = "ledger.account.archive";
    static final String OPENING_CORRECTION = "ledger.account.opening-correction";
    static final String CASH_ACTIVITY = "ledger.cash-activity";
    static final String TRANSFER = "ledger.transfer";
    static final String ACTIVITY_REVERSAL = "ledger.activity-reversal";
    static final String RECONCILIATION_COMMIT = "ledger.reconciliation.commit";
    static final String RECONCILIATION_CORRECTION = "ledger.reconciliation.correction";

    private LedgerCommandScopes() {}
}
