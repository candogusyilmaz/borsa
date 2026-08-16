package dev.canverse.stocks.reference.web.response;

import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InstrumentSummaryResponse(
        @NotNull UUID id,
        @NotNull String symbol,
        @NotNull String name,
        @NotNull InstrumentType instrumentType,
        @NotNull UUID marketId,
        @NotNull String marketCode,
        @NotNull String quotationCurrency,
        @NotNull ValuationMethod valuationMethod,
        boolean active,
        @NotNull String sourceKind,
        boolean ownerManaged,
        @NotNull List<InstrumentAliasResponse> aliases) {

    public InstrumentSummaryResponse {
        aliases = List.copyOf(aliases);
    }

    public static InstrumentSummaryResponse from(ReferenceCatalogReadRepository.InstrumentView view) {
        var row = view.row();
        var aliases = view.aliases().stream().map(InstrumentAliasResponse::from).toList();
        return new InstrumentSummaryResponse(
                row.id(),
                row.symbol(),
                row.name(),
                row.instrumentType(),
                row.marketId(),
                row.marketCode(),
                row.quotationCurrency(),
                row.valuationMethod(),
                row.active(),
                row.sourceKind(),
                row.ownerId() != null,
                aliases);
    }
}
