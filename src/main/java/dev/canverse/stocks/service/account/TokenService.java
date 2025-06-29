package dev.canverse.stocks.service.account;

import dev.canverse.stocks.domain.entity.User;
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
    private final static long ACCESS_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 1;
    private final static long REFRESH_TOKEN_EXPIRATION_IN_SECONDS = 60 * 60 * 24 * 7;

    private final UserDetailsService userService;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public User getSecurityUser(String refreshToken) {
        var jwt = jwtDecoder.decode(refreshToken);
        var username = jwt.getClaimAsString("sub");
        return (User) userService.loadUserByUsername(username);
    }

    public ResponseEntity<TokenCreateResponse> create(User principal) {
        var body = this.createAccessToken(principal);
        var cookie = this.createRefreshTokenCookie(principal);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(body);
    }

    public String createRefreshTokenCookie(User principal) {
        var now = Instant.now();
        var expiresAt = now.plus(REFRESH_TOKEN_EXPIRATION_IN_SECONDS, ChronoUnit.SECONDS);

        var claims = JwtClaimsSet.builder()
                .issuer("finance-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(principal.getEmail())
                .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return ResponseCookie.from("refresh-token", token)
                .path("/")
                .maxAge(REFRESH_TOKEN_EXPIRATION_IN_SECONDS)
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
                .issuer("finance-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(principal.getEmail())
                .build();

        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return TokenCreateResponse.from(principal, token);
    }
}