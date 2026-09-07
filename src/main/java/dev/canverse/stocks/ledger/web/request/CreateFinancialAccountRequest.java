package dev.canverse.stocks.ledger.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.platform.error.ValidationErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateFinancialAccountRequest(@NotNull UUID clientRequestId, @NotBlank @Size(max = 160) String name,
        @NotNull @JsonAlias("accountKind") AccountKind kind, @NotNull TrackingMode trackingMode, @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 120) String timeZone, @JsonAlias({
                "negativeBalancePolicy", "policy"}) NegativeBalancePolicy policy,
        String authorizedLimit, @Valid OpeningStateRequest openingState) {

    public void validate() {
        if (trackingMode == TrackingMode.FULL_LEDGER && openingState == null) {
            throw ValidationErrors.invalidField("openingState", "error.fields.ledger.required", "A full-ledger account requires an opening state.");
        }
        if (trackingMode == TrackingMode.HOLDINGS_ONLY && openingState != null) {
            throw ValidationErrors.invalidField("openingState", "error.fields.ledger.forbidden", "A holdings-only account cannot have cash opening state.");
        }
    }
}
