package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.AuthenticatedIdentityResolver;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedIdentityResolverTest {

    private final AuthenticatedIdentityResolver resolver = new AuthenticatedIdentityResolver();

    @Test
    void resolvesValidAuthenticatedJwtToken() {
        var userAccountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        var sessionId = UUID.fromString("22222222-2222-4222-8222-222222222222");

        var jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", userAccountId.toString())
                .claim("sid", sessionId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var authentication = new JwtAuthenticationToken(jwt, List.of(), userAccountId.toString());

        var identity = resolver.resolve(authentication);

        assertThat(identity.userAccountId()).isEqualTo(userAccountId);
        assertThat(identity.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void rejectsNullAuthentication() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsUnauthenticatedToken() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .build();

        var auth = mock(JwtAuthenticationToken.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(auth.getToken()).thenReturn(jwt);

        assertThatThrownBy(() -> resolver.resolve(auth))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsWrongAuthenticationType() {
        Authentication auth = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> resolver.resolve(auth))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));

        Authentication userPassAuth = new UsernamePasswordAuthenticationToken("user", "pass");
        assertThatThrownBy(() -> resolver.resolve(userPassAuth))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsMissingOrNonStringClaims() {
        var userAccountId = UUID.randomUUID();

        var jwtMissingSid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", userAccountId.toString())
                .build();
        var authMissingSid = new JwtAuthenticationToken(jwtMissingSid, List.of(), userAccountId.toString());

        assertThatThrownBy(() -> resolver.resolve(authMissingSid))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));

        var jwtNumericSid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", userAccountId.toString())
                .claim("sid", 12345)
                .build();
        var authNumericSid = new JwtAuthenticationToken(jwtNumericSid, List.of(), userAccountId.toString());

        assertThatThrownBy(() -> resolver.resolve(authNumericSid))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsNonCanonicalUuids() {
        var userAccountId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();

        var uppercaseSub = userAccountId.toString().toUpperCase(Locale.ROOT);
        var jwtUpperSub = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", uppercaseSub)
                .claim("sid", sessionId.toString())
                .build();
        var authUpperSub = new JwtAuthenticationToken(jwtUpperSub, List.of(), userAccountId.toString());

        assertThatThrownBy(() -> resolver.resolve(authUpperSub))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));

        var invalidUuidString = "not-a-valid-uuid";
        var jwtInvalid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", invalidUuidString)
                .claim("sid", sessionId.toString())
                .build();
        var authInvalid = new JwtAuthenticationToken(jwtInvalid, List.of(), invalidUuidString);

        assertThatThrownBy(() -> resolver.resolve(authInvalid))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsNameMismatch() {
        var userAccountId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();

        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", userAccountId.toString())
                .claim("sid", sessionId.toString())
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(), otherUserId.toString());

        assertThatThrownBy(() -> resolver.resolve(auth))
                .isInstanceOf(AppException.class)
                .satisfies(e ->
                        assertThat(((AppException) e).getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS));
    }
}
