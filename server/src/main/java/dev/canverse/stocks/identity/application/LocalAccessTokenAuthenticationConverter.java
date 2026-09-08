package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LocalAccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String INVALID_BEARER_TOKEN_MESSAGE = "The bearer token is invalid.";

    private final DeviceSessionRepository deviceSessionRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");

        var userAccountId = parseCanonicalUuid(jwt.getClaims().get("sub"));
        var sessionId = parseCanonicalUuid(jwt.getClaims().get("sid"));
        var observedAt = clock.instant();
        deviceSessionRepository.findOwnedById(sessionId, userAccountId).filter(session -> session.isActiveAndUserEnabled(observedAt))
                .orElseThrow(this::invalidBearerToken);

        return new AuthenticatedIdentityToken(new AuthenticatedIdentity(userAccountId, sessionId), jwt);
    }

    private UUID parseCanonicalUuid(Object claim) {
        if (!(claim instanceof String value)) {
            throw invalidBearerToken();
        }
        try {
            var parsed = UUID.fromString(value);
            if (parsed.toString().equals(value)) {
                return parsed;
            }
        } catch (IllegalArgumentException exception) {
            // The safe bearer failure below intentionally hides malformed claim detail.
        }
        throw invalidBearerToken();
    }

    private InvalidBearerTokenException invalidBearerToken() {
        return new InvalidBearerTokenException(INVALID_BEARER_TOKEN_MESSAGE);
    }
}
