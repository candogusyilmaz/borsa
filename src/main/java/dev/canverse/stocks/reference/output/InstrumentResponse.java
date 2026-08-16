package dev.canverse.stocks.reference.output;

import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstrumentResponse(
        @NotNull UUID id,
        UUID ownerId,
        @NotNull UUID marketId,
        @NotNull String marketCode,
        @NotNull String symbol,
        @NotNull String name,
        @NotNull InstrumentType instrumentType,
        @NotNull String quotationCurrency,
        @NotNull ValuationMethod valuationMethod,
        boolean active,
        @NotNull String sourceKind,
        long version,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull List<InstrumentAliasResponse> aliases) {

    public InstrumentResponse {
        aliases = List.copyOf(aliases);
    }

    public static InstrumentResponse from(ReferenceCatalogReadRepository.InstrumentView view) {
        var row = view.row();
        var aliases = view.aliases().stream().map(InstrumentAliasResponse::from).toList();
        return new InstrumentResponse(
                row.id(),
                row.ownerId(),
                row.marketId(),
                row.marketCode(),
                row.symbol(),
                row.name(),
                row.instrumentType(),
                row.quotationCurrency(),
                row.valuationMethod(),
                row.active(),
                row.sourceKind(),
                row.version(),
                row.createdAt(),
                row.updatedAt(),
                aliases);
    }
}
