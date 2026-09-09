package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.AuthenticationAttemptService;
import dev.canverse.stocks.identity.application.model.LocalAuthenticationResult;
import dev.canverse.stocks.identity.web.request.LocalLoginRequest;
import dev.canverse.stocks.identity.web.request.LocalRefreshRequest;
import dev.canverse.stocks.identity.web.request.RefreshTokenDelivery;
import dev.canverse.stocks.identity.web.request.RegistrationRequest;
import dev.canverse.stocks.identity.web.response.LocalAuthenticationResponse;
import dev.canverse.stocks.identity.web.response.RegistrationResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LocalAuthenticationController {

    private static final String REFRESH_COOKIE_NAME = "refresh-token";

    private final AuthenticationAttemptService authenticationAttemptService;
    private final Clock clock;

    @PostMapping("register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        var userId = authenticationAttemptService.attemptRegistration(request.email(), request.password(), servletRequest.getRemoteAddr(),
                traceId(servletRequest));
        return new RegistrationResponse(userId);
    }

    @PostMapping("login")
    public ResponseEntity<LocalAuthenticationResponse> login(@Valid @RequestBody LocalLoginRequest request, HttpServletRequest servletRequest) {
        var result = authenticationAttemptService.attemptLogin(request.email(), request.password(), request.deviceLabel(), servletRequest.getRemoteAddr(),
                traceId(servletRequest));
        return credentialResponse(result, request.refreshTokenDelivery());
    }

    @PostMapping(value = "refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LocalAuthenticationResponse> refresh(@Valid @RequestBody LocalRefreshRequest request, HttpServletRequest servletRequest) {
        var result = authenticationAttemptService.attemptRefresh(request, refreshCookieValues(servletRequest), servletRequest.getRemoteAddr(),
                traceId(servletRequest));
        return credentialResponse(result, request.refreshTokenDelivery());
    }

    private ResponseEntity<LocalAuthenticationResponse> credentialResponse(LocalAuthenticationResult result, RefreshTokenDelivery delivery) {
        var serverTime = clock.instant();
        var responseRefreshToken = delivery == RefreshTokenDelivery.RESPONSE_BODY ? result.refreshToken() : null;
        var response = LocalAuthenticationResponse.from(result, serverTime, responseRefreshToken);

        var headers = CacheHeaders.noStore();
        if (delivery == RefreshTokenDelivery.HTTP_ONLY_COOKIE) {
            headers.add(HttpHeaders.SET_COOKIE, RefreshTokenCookieHeader.create(result.refreshToken(), result.refreshTokenExpiresAt(), serverTime));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    private String traceId(HttpServletRequest servletRequest) {
        var traceId = (String) servletRequest.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        return traceId == null ? "unknown" : traceId;
    }

    private List<String> refreshCookieValues(HttpServletRequest servletRequest) {
        var cookies = servletRequest.getCookies();
        if (cookies == null) {
            return List.of();
        }
        return Arrays.stream(cookies).filter(cookie -> Objects.equals(REFRESH_COOKIE_NAME, cookie.getName())).map(Cookie::getValue).toList();
    }
}
