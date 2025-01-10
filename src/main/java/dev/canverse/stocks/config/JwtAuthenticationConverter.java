package dev.canverse.stocks.config;

import dev.canverse.stocks.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository userRepository;

    @Override
    @SuppressWarnings("NullableProblems")
    public AbstractAuthenticationToken convert(Jwt source) {
        var user = userRepository.findByUsernameIncludePermissions(source.getSubject())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
