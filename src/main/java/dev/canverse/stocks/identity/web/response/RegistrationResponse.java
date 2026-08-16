package dev.canverse.stocks.identity.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegistrationResponse(@NotNull UUID userId) {}
