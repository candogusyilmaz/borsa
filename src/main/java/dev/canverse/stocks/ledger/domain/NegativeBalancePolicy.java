package dev.canverse.stocks.ledger.domain;

public enum NegativeBalancePolicy {
    HARD_FLOOR,
    SOFT_FLOOR,
    TRACK_REALITY,
    AUTHORIZED_LIMIT
}
