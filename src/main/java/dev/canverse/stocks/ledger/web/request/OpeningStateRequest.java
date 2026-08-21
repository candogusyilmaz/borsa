package dev.canverse.stocks.ledger.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record OpeningStateRequest(
        @NotBlank String amount, @NotNull Instant effectiveAt) {}
