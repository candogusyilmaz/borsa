package dev.canverse.stocks.identity.application.model;

import java.time.Instant;

public record IssuedAccessToken(String accessToken, Instant expiresAt) {}
