package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthenticatedIdentityToken extends AbstractAuthenticationToken {

    private final AuthenticatedIdentity principal;
    private final Jwt credentials;

    public AuthenticatedIdentityToken(AuthenticatedIdentity principal, Jwt credentials) {
        super(List.of());
        this.principal = Objects.requireNonNull(principal, "principal");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedIdentity getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return credentials;
    }

    @Override
    public String getName() {
        return principal.userAccountId().toString();
    }
}
