package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.PostingRole;
import java.util.UUID;

public record PostingView(UUID accountId, UUID pocketId, String currencyCode, String amount, PostingRole role) {}
