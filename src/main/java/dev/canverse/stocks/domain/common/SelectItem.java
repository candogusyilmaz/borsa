package dev.canverse.stocks.domain.common;

import jakarta.validation.constraints.NotNull;

public record SelectItem(@NotNull String value, @NotNull String label) {
}
