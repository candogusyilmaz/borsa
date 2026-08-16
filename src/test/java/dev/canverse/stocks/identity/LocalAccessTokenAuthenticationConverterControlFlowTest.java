package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class LocalAccessTokenAuthenticationConverterControlFlowTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-09T14:00:00Z");
    private static final String USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String SESSION_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String SAFE_MESSAGE = "The bearer token is invalid.";

    @Test
    void nullJwtFailsBeforeTimeOrQueryWork() {
        var repository = mock(DeviceSessionRepository.class);
        var clock = mock(Clock.class);
        var converter = new LocalAccessTokenAuthenticationConverter(repository, clock);

        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("jwt");

        verifyNoInteractions(clock, repository);
    }

    @Test
    void missingMalformedAndNonCanonicalIdentityClaimsFailSafelyBeforeTimeOrQueryWork() {
        var invalidClaims = List.of(
                new InvalidClaims("missing subject", claims("sid", SESSION_ID)),
                new InvalidClaims("malformed subject", claims("sub", "not-a-uuid", "sid", SESSION_ID)),
                new InvalidClaims("non-canonical subject", claims("sub", USER_ID.toUpperCase(), "sid", SESSION_ID)),
                new InvalidClaims("missing session", claims("sub", USER_ID)),
                new InvalidClaims("malformed session", claims("sub", USER_ID, "sid", "not-a-uuid")),
                new InvalidClaims("non-canonical session", claims("sub", USER_ID, "sid", SESSION_ID.toUpperCase())));

        invalidClaims.forEach(invalid -> {
            var repository = mock(DeviceSessionRepository.class);
            var clock = mock(Clock.class);
            var converter = new LocalAccessTokenAuthenticationConverter(repository, clock);

            var thrown = catchThrowable(() -> converter.convert(jwt(invalid.claims())));

            assertSafeBearerFailure(thrown, invalid.name());
            verifyNoInteractions(clock, repository);
        });
    }

    @Test
    void validIdentityObservesTimeAndPerformsOneOwnerScopedLookupInOrder() {
        var repository = mock(DeviceSessionRepository.class);
        var clock = mock(Clock.class);
        var observedAt = Instant.parse("2026-08-09T14:00:00.750Z");
        var userAccountId = UUID.fromString(USER_ID);
        var sessionId = UUID.fromString(SESSION_ID);
        when(clock.instant()).thenReturn(observedAt);
        when(repository.findOwnedById(sessionId, userAccountId)).thenReturn(Optional.empty());
        var converter = new LocalAccessTokenAuthenticationConverter(repository, clock);

        var thrown = catchThrowable(() -> converter.convert(jwt(claims("sub", USER_ID, "sid", SESSION_ID))));

        assertSafeBearerFailure(thrown, "unknown exact pair");
        var ordered = inOrder(clock, repository);
        ordered.verify(clock).instant();
        ordered.verify(repository).findOwnedById(sessionId, userAccountId);
        verifyNoMoreInteractions(clock, repository);
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("direct-test-token", ISSUED_AT, ISSUED_AT.plusSeconds(300), Map.of("alg", "RS256"), claims);
    }

    private Map<String, Object> claims(Object... entries) {
        var claims = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            claims.put((String) entries[index], entries[index + 1]);
        }
        return Map.copyOf(claims);
    }

    private void assertSafeBearerFailure(Throwable thrown, String caseName) {
        assertThat(thrown).as(caseName).isExactlyInstanceOf(InvalidBearerTokenException.class);
        var exception = (InvalidBearerTokenException) thrown;
        assertThat(exception.getError().getErrorCode()).as(caseName).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(exception.getMessage()).as(caseName).isEqualTo(SAFE_MESSAGE);
        assertThat(exception.toString())
                .as(caseName)
                .doesNotContain("direct-test-token", caseName, USER_ID, SESSION_ID, "not-a-uuid");
    }

    private record InvalidClaims(String name, Map<String, Object> claims) {}
}
