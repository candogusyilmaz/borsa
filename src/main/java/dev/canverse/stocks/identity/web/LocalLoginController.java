package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalLoginResult;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.identity.input.LocalLoginRequest;
import dev.canverse.stocks.identity.input.RefreshTokenDelivery;
import dev.canverse.stocks.identity.output.LocalLoginResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalLoginController {

    private static final String REFRESH_COOKIE_NAME = "refresh-token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Pattern COOKIE_EXPIRES_ATTRIBUTE = Pattern.compile("(?i)Expires=[^;]+");

    private final LocalLoginService loginService;
    private final Clock clock;

    public LocalLoginController(LocalLoginService loginService, Clock clock) {
        this.loginService = loginService;
        this.clock = clock;
    }

    @PostMapping("login")
    public ResponseEntity<LocalLoginResponse> login(@Valid @RequestBody LocalLoginRequest request) {
        var loginResult = loginService.login(request.email(), request.password(), request.deviceLabel());
        var serverTime = clock.instant();
        var refreshToken = request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY
                ? loginResult.refreshToken()
                : null;
        var response = toResponse(loginResult, serverTime, refreshToken);

        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE) {
            var refreshTokenExpiresAt = loginResult.refreshTokenExpiresAt();
            var maxAgeSeconds = Duration.between(serverTime, loginResult.refreshTokenExpiresAt())
                    .toSeconds();
            var refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, loginResult.refreshToken())
                    .path(REFRESH_COOKIE_PATH)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .maxAge(maxAgeSeconds)
                    .build();
            headers.add(HttpHeaders.SET_COOKIE, cookieHeader(refreshCookie, refreshTokenExpiresAt));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    private static String cookieHeader(ResponseCookie refreshCookie, Instant refreshTokenExpiresAt) {
        var expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                refreshTokenExpiresAt.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneOffset.UTC));
        return COOKIE_EXPIRES_ATTRIBUTE
                .matcher(refreshCookie.toString())
                .replaceFirst(Matcher.quoteReplacement("Expires=" + expires));
    }

    private LocalLoginResponse toResponse(LocalLoginResult loginResult, Instant serverTime, String refreshToken) {
        return new LocalLoginResponse(
                loginResult.sessionId(),
                loginResult.accessToken(),
                loginResult.accessTokenExpiresAt(),
                loginResult.refreshTokenExpiresAt(),
                serverTime,
                refreshToken);
    }
}
