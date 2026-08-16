package dev.canverse.stocks.identity.web.request;

import jakarta.validation.constraints.NotNull;

public record LogoutRequest(@NotNull LogoutScope scope) {}
