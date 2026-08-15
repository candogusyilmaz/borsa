package dev.canverse.stocks.identity.input;

import jakarta.validation.constraints.NotNull;

public record LogoutRequest(@NotNull LogoutScope scope) {}
