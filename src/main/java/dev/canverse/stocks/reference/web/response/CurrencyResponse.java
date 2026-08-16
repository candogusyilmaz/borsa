package dev.canverse.stocks.reference.web.response;

import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;

public record CurrencyResponse(
        @NotNull String code, @NotNull String name, @NotNull String symbol, int minorUnit, boolean active) {

    public static CurrencyResponse from(ReferenceCatalogReadRepository.CurrencyRow row) {
        return new CurrencyResponse(row.code(), row.name(), row.symbol(), row.minorUnit(), row.active());
    }
}
