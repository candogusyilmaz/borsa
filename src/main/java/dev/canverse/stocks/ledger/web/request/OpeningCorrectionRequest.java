package dev.canverse.stocks.ledger.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OpeningCorrectionRequest(
        @NotNull UUID clientRequestId,
        @NotBlank String amount,
        @NotNull Instant effectiveAt,
        @NotBlank @Size(max = 500) String correctionReason,
        @NotNull @PositiveOrZero Long version) {}
