package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.ledger.domain.ReconciliationResolution;

/** Explicit API action for recording the derived reconciliation resolution. */
public enum ReconciliationAction {
    CONFIRM_BALANCED,
    CREATE_ADJUSTMENT;

    public ReconciliationResolution resolution() {
        return this == CONFIRM_BALANCED ? ReconciliationResolution.BALANCED : ReconciliationResolution.ADJUSTED;
    }
}
