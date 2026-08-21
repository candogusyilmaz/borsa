package dev.canverse.stocks.ledger.domain;

public enum PostingRole {
    OPENING,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_SOURCE,
    TRANSFER_DESTINATION,
    REVERSAL
}
