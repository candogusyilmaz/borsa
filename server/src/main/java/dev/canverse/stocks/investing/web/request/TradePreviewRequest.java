package dev.canverse.stocks.investing.web.request;

import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

public record TradePreviewRequest(
        @NotNull UUID accountId,
        @NotNull UUID instrumentId,
        @NotNull TradeSide side,
        @NotBlank String quantity,
        @NotBlank String unitPrice,
        @NotBlank String commissionAmount,
        @NotNull RecordingMode recordingMode,
        @NotNull Instant effectiveAt,
        @NotNull @PositiveOrZero Long economicSequence,
        @NotNull Boolean confirmPolicyBreach
) {}
