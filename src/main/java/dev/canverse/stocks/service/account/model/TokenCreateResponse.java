package dev.canverse.stocks.service.account.model;

import dev.canverse.stocks.domain.entity.account.User;

import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record TokenCreateResponse(
        @NotNull String name,
        @NotNull String email,
        @NotNull List<String> permissions) {
    public static TokenCreateResponse from(User user) {
        return new TokenCreateResponse(
                user.getName(),
                user.getEmail(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
