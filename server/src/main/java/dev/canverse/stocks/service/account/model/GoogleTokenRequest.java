package dev.canverse.stocks.service.account.model;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(@NotBlank String code, String redirectUri) {}
