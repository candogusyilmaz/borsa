package dev.canverse.stocks.ledger.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

public record CashActivityRequest(
        @NotNull UUID clientRequestId,
        @NotNull @JsonAlias("type") ActivityType activityType,
        @NotBlank String amount,
        @NotNull RecordingMode recordingMode,
        @NotNull Instant effectiveAt,
        boolean confirmPolicyBreach,
        @PositiveOrZero Long expectedBalanceVersion) {}
