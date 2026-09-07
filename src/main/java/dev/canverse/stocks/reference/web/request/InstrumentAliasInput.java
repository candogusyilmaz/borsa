package dev.canverse.stocks.reference.web.request;

import dev.canverse.stocks.reference.domain.AliasType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InstrumentAliasInput(@NotNull AliasType type, @NotBlank String value) {}
