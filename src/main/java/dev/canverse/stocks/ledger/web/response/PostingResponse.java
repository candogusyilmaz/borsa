package dev.canverse.stocks.ledger.web.response;

import dev.canverse.stocks.ledger.application.model.PostingView;
import dev.canverse.stocks.ledger.domain.PostingRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PostingResponse(
        @NotNull UUID accountId,
        @NotNull UUID pocketId,
        @NotNull String currency,
        @NotNull String amount,
        @NotNull PostingRole role) {

    public static PostingResponse from(PostingView view) {
        return new PostingResponse(view.accountId(), view.pocketId(), view.currencyCode(), view.amount(), view.role());
    }
}
