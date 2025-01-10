package dev.canverse.stocks.service.authentication.model;

import dev.canverse.stocks.domain.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

public record TokenCreateResponse(
        String token,
        List<String> permissions) {
    public static TokenCreateResponse from(User user, String token) {
        return new TokenCreateResponse(
                token,
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        );
    }
}