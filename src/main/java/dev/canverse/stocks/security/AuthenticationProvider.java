package dev.canverse.stocks.security;

import dev.canverse.stocks.domain.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthenticationProvider {
    public static User getUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
