package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalLoginAttemptService;
import dev.canverse.stocks.identity.web.request.LocalLoginRequest;
import dev.canverse.stocks.identity.web.request.RefreshTokenDelivery;
import dev.canverse.stocks.identity.web.response.LocalLoginResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
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

    private final LocalLoginAttemptService loginAttemptService;
    private final Clock clock;

    public LocalLoginController(LocalLoginAttemptService loginAttemptService, Clock clock) {
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    @PostMapping("login")
    public ResponseEntity<LocalLoginResponse> login(
            @Valid @RequestBody LocalLoginRequest request, HttpServletRequest servletRequest) {
        var remoteAddr = servletRequest.getRemoteAddr();
        var traceId = (String) servletRequest.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        if (traceId == null) {
            traceId = "unknown";
        }

        var loginResult = loginAttemptService.attemptLogin(
                request.email(), request.password(), request.deviceLabel(), remoteAddr, traceId);
        var serverTime = clock.instant();
        var refreshToken = request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY
                ? loginResult.refreshToken()
                : null;
        var response = LocalLoginResponse.from(loginResult, serverTime, refreshToken);

        var headers = CacheHeaders.noStore();
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    RefreshTokenCookieHeader.create(
                            loginResult.refreshToken(), loginResult.refreshTokenExpiresAt(), serverTime));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }
}
