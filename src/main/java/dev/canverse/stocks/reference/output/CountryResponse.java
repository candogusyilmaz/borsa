package dev.canverse.stocks.reference.output;

import dev.canverse.stocks.reference.infrastructure.ReferenceCatalogReadRepository;
import jakarta.validation.constraints.NotNull;

public record CountryResponse(@NotNull String code, @NotNull String name, boolean active) {

    public static CountryResponse from(ReferenceCatalogReadRepository.CountryRow row) {
        return new CountryResponse(row.code(), row.name(), row.active());
    }
}
