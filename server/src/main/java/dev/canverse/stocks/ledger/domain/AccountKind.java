package dev.canverse.stocks.ledger.domain;

public enum AccountKind {
    CASH_CURRENT, CASH_SAVINGS, CASH_WALLET, BROKERAGE, CREDIT_CARD, LOAN;

    public boolean isLiability() {
        return this == CREDIT_CARD || this == LOAN;
    }

    public boolean isAsset() {
        return !isLiability();
    }

    public boolean supportsHoldingsOnly() {
        return this == BROKERAGE;
    }

    public boolean isCashFundingCapable() {
        return this == CASH_CURRENT || this == CASH_SAVINGS || this == CASH_WALLET || this == BROKERAGE;
    }

    public boolean supportsNegativePolicy(NegativeBalancePolicy policy) {
        if (isLiability() || policy == null) {
            return false;
        }
        return switch (this) {
            case CASH_CURRENT -> true;
            case CASH_SAVINGS, CASH_WALLET, BROKERAGE -> policy != NegativeBalancePolicy.AUTHORIZED_LIMIT;
            case CREDIT_CARD, LOAN -> false;
        };
    }
}
