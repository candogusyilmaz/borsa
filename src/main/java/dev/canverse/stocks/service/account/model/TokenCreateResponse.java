package dev.canverse.stocks.service.account.model;

import dev.canverse.stocks.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record TokenCreateResponse(
        String name,
        String email,
        String token,
        List<String> permissions) {
    public static TokenCreateResponse from(User user, String token) {
        return new TokenCreateResponse(
                user.getName(),
                user.getEmail(),
                token,
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        );
    }
}