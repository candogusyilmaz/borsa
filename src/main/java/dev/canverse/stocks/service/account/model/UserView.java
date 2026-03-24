package dev.canverse.stocks.service.account.model;

import dev.canverse.stocks.domain.entity.account.User;

import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.List;

public record UserView(
        @NotNull Long id,
        @NotNull String name,
        @NotNull String email,
        @NotNull Instant lastLoginAt,
        @NotNull Instant createdAt,
        @NotNull boolean onboardingCompleted,
        @NotNull List<String> permissions) {

    public static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getOnboardingCompleted(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
