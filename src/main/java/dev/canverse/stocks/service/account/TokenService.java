package dev.canverse.stocks.service.account;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.service.account.model.TokenCreateResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TokenService {
    private static final long ACCESS_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 1;
    private static final long REFRESH_TOKEN_REMEMBER_ME_EXPIRATION_IN_SECONDS = 60 * 60 * 24 * 30;
    private static final long REFRESH_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 24;

    private final UserDetailsService userService;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public User getSecurityUser(String refreshToken) {
        var jwt = jwtDecoder.decode(refreshToken);
        var username = jwt.getClaimAsString("sub");
        return (User) userService.loadUserByUsername(username);
    }

    public boolean getRememberMe(String refreshToken) {
        var jwt = jwtDecoder.decode(refreshToken);
        Boolean rememberMe = jwt.getClaimAsBoolean("rememberMe");
        return Boolean.TRUE.equals(rememberMe);
    }

    public ResponseEntity<TokenCreateResponse> create(User principal, boolean rememberMe) {
        var accessTokenCookie = this.createAccessTokenCookie(principal);
        var refreshTokenCookie = this.createRefreshTokenCookie(principal, rememberMe);
        var body = TokenCreateResponse.from(principal);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie)
                .body(body);
    }

    public ResponseEntity<Void> clearCookies() {
        var clearAccessToken = ResponseCookie.from("access-token", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("none")
                .build()
                .toString();

        var clearRefreshToken = ResponseCookie.from("refresh-token", "")
                .path("/api/auth/refresh-token")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("none")
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessToken)
                .header(HttpHeaders.SET_COOKIE, clearRefreshToken)
                .build();
    }

    public String createRefreshTokenCookie(User principal, boolean rememberMe) {
        var now = Instant.now();
        long expirationSeconds = rememberMe
                ? REFRESH_TOKEN_REMEMBER_ME_EXPIRATION_IN_SECONDS
                : REFRESH_TOKEN_EXPIRATION_IN_SECONDS;
        var expiresAt = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        var claims =
                JwtClaimsSet.builder()
                        .issuer("finance-api")
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(principal.getEmail())
                        .claim("rememberMe", rememberMe)
                        .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return ResponseCookie.from("refresh-token", token)
                .path("/api/auth/refresh-token")
                .maxAge(expirationSeconds)
                .httpOnly(true)
                .secure(true)
                .sameSite("none")
                .build()
                .toString();
    }

    public String createAccessTokenCookie(User principal) {
        var now = Instant.now();
        var expiresAt = now.plus(ACCESS_TOKEN_EXPIRATION_IN_SECONDS, ChronoUnit.SECONDS);

        var claims =
                JwtClaimsSet.builder()
                        .issuer("finance-api")
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .subject(principal.getEmail())
                        .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return ResponseCookie.from("access-token", token)
                .path("/")
                .maxAge(ACCESS_TOKEN_EXPIRATION_IN_SECONDS)
                .httpOnly(true)
                .secure(true)
                .sameSite("none")
                .build()
                .toString();
    }
}
