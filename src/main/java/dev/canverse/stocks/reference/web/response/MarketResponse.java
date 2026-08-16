package dev.canverse.stocks.reference.web.response;

import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record MarketResponse(
        @NotNull UUID id,
        @NotNull String code,
        @NotNull String name,
        @NotNull String marketType,
        String countryCode,
        @NotNull String timeZone,
        @NotNull List<String> quotationCurrencies,
        String primaryQuotationCurrency,
        boolean active,
        @NotNull String sourceKind) {

    public MarketResponse {
        quotationCurrencies = List.copyOf(quotationCurrencies);
    }

    public static MarketResponse from(ReferenceCatalogReadRepository.MarketRow row) {
        return new MarketResponse(
                row.id(),
                row.code(),
                row.name(),
                row.marketType(),
                row.countryCode(),
                row.timeZone(),
                row.quotationCurrencies(),
                row.primaryQuotationCurrency(),
                row.active(),
                row.sourceKind());
    }
}
