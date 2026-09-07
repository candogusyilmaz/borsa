package dev.canverse.stocks.reference.web.response;

import jakarta.validation.constraints.NotNull;

public record CountryResponse(@NotNull String code, @NotNull String name, boolean active) {}
