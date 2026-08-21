package dev.canverse.stocks.ledger.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FinancialAccountPageResponse(@NotNull List<FinancialAccountResponse> accounts, String nextCursor) {
    public FinancialAccountPageResponse {
        accounts = List.copyOf(accounts);
    }
}
