package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record AccountPolicyRequest(@NotNull UUID clientRequestId, NegativeBalancePolicy policy, String authorizedLimit,
        @NotNull @PositiveOrZero Long version) {}
