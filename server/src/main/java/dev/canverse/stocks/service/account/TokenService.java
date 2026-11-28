package dev.canverse.stocks.service.account;

import static dev.canverse.stocks.security.JwtConfiguration.ACCESS_TOKEN_USE;
import static dev.canverse.stocks.security.JwtConfiguration.ISSUER;
import static dev.canverse.stocks.security.JwtConfiguration.REFRESH_TOKEN_USE;
import static dev.canverse.stocks.security.JwtConfiguration.TOKEN_USE_CLAIM;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.service.account.model.TokenCreateResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final long ACCESS_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 1;
    private static final long REFRESH_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 24 * 7;

    private final UserDetailsService userService;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;

    public TokenService(UserDetailsService userService, JwtEncoder jwtEncoder,
        @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder) {
        this.userService = userService;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
    }

    public User getSecurityUser(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadJwtException("Refresh token is missing");
        }

        var jwt = refreshTokenDecoder.decode(refreshToken);
        var username = jwt.getClaimAsString("sub");
        if (username == null || username.isBlank()) {
            throw new BadJwtException("Refresh token subject is missing");
        }

        return (User) userService.loadUserByUsername(username);
    }

    public ResponseEntity<TokenCreateResponse> create(User principal) {
        var body = this.createAccessToken(principal);
        var cookie = this.createRefreshTokenCookie(principal);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(body);
    }

    public String createRefreshTokenCookie(User principal) {
        var now = Instant.now();
        var expiresAt = now.plus(REFRESH_TOKEN_EXPIRATION_IN_SECONDS, ChronoUnit.SECONDS);

        var claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(principal.getEmail())
            .claim(TOKEN_USE_CLAIM, REFRESH_TOKEN_USE)
            .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return buildRefreshTokenCookie(token, REFRESH_TOKEN_EXPIRATION_IN_SECONDS);
    }

    public String clearRefreshTokenCookie() {
        return buildRefreshTokenCookie("", 0);
    }

    private static String buildRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("refresh-token", token)
            .path("/")
            .maxAge(maxAgeSeconds)
            .httpOnly(true)
            .secure(true)
            .sameSite("none")
            .build()
            .toString();
    }

    public TokenCreateResponse createAccessToken(User principal) {
        var now = Instant.now();
        var expiresAt = now.plus(ACCESS_TOKEN_EXPIRATION_IN_SECONDS, ChronoUnit.SECONDS);

        var claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(principal.getEmail())
            .claim(TOKEN_USE_CLAIM, ACCESS_TOKEN_USE)
            .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return TokenCreateResponse.from(principal, token);
    }
}
