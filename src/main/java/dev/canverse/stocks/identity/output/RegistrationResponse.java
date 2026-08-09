package dev.canverse.stocks.identity.output;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegistrationResponse(@NotNull UUID userId) {}
