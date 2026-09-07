package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

public record TransferRequest(@NotNull UUID clientRequestId, @NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId, @NotBlank String amount,
        @NotNull RecordingMode recordingMode, @NotNull Instant effectiveAt, boolean confirmPolicyBreach, @PositiveOrZero Long expectedSourceBalanceVersion,
        @PositiveOrZero Long expectedDestinationBalanceVersion) {}
