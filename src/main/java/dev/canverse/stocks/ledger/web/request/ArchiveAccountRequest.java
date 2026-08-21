package dev.canverse.stocks.ledger.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ArchiveAccountRequest(
        @NotNull UUID clientRequestId,
        @NotNull @PositiveOrZero Long version) {}
