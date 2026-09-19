package dev.canverse.stocks.ledger.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

public record CashActivityRequest(@NotNull UUID clientRequestId,
        @NotNull @JsonAlias("type") @Schema(implementation = String.class,
                allowableValues = {"CASH_DEPOSIT", "CASH_WITHDRAWAL", "CASH_FEE", "CASH_INTEREST_CREDIT"}) ActivityType activityType,
        @NotBlank String amount, @NotNull RecordingMode recordingMode, @NotNull Instant effectiveAt, boolean confirmPolicyBreach,
        @PositiveOrZero Long expectedBalanceVersion) {}
