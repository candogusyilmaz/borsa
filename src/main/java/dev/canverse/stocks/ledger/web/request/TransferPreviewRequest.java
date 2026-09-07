package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record TransferPreviewRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId, @NotBlank String amount,
        @NotNull RecordingMode recordingMode, @NotNull Instant effectiveAt, boolean confirmPolicyBreach) {}
