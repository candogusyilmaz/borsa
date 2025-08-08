package dev.canverse.stocks.security;

import dev.canverse.stocks.domain.entity.account.User;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringSecurityAuditorAware implements AuditorAware<User> {

    @Override
    public Optional<User> getCurrentAuditor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null)
            throw new AuthenticationCredentialsNotFoundException("No user is authenticated.");

        return Optional.of((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}