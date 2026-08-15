package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalLoginResult;
import dev.canverse.stocks.identity.application.LocalLoginService;
import dev.canverse.stocks.identity.input.LocalLoginRequest;
import dev.canverse.stocks.identity.input.RefreshTokenDelivery;
import dev.canverse.stocks.identity.output.LocalLoginResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalLoginController {

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
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    RefreshTokenCookieHeader.create(
                            loginResult.refreshToken(), loginResult.refreshTokenExpiresAt(), serverTime));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
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
