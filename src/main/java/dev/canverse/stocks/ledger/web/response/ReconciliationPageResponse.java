package dev.canverse.stocks.ledger.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReconciliationPageResponse(@NotNull List<ReconciliationResponse> reconciliations, String nextCursor) {

    public ReconciliationPageResponse {
        reconciliations = List.copyOf(reconciliations);
    }
}
