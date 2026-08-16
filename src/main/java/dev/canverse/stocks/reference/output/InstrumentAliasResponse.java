package dev.canverse.stocks.reference.output;

import dev.canverse.stocks.reference.domain.AliasType;
import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InstrumentAliasResponse(
        @NotNull UUID id, @NotNull AliasType type, @NotNull String value) {

    public static InstrumentAliasResponse from(ReferenceCatalogReadRepository.AliasRow row) {
        return new InstrumentAliasResponse(row.id(), row.type(), row.value());
    }
}
