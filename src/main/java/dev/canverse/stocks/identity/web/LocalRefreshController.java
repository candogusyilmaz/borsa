package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalRefreshResult;
import dev.canverse.stocks.identity.application.RefreshSessionRotationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.input.LocalRefreshRequest;
import dev.canverse.stocks.identity.input.RefreshTokenDelivery;
import dev.canverse.stocks.identity.output.LocalRefreshResponse;
import dev.canverse.stocks.platform.error.AppException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalRefreshController {

    private static final String REFRESH_COOKIE_NAME = "refresh-token";

    private final RefreshSessionRotationService rotationService;
    private final Clock clock;

    public LocalRefreshController(RefreshSessionRotationService rotationService, Clock clock) {
        this.rotationService = rotationService;
        this.clock = clock;
    }

    @PostMapping(value = "refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LocalRefreshResponse> refresh(
            @Valid @RequestBody LocalRefreshRequest request, HttpServletRequest servletRequest) {
        var rawRefreshToken = selectCredential(request, servletRequest);
        var result = rotationService
                .rotate(rawRefreshToken)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
        var serverTime = clock.instant();
        var responseRefreshToken =
                request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY ? result.refreshToken() : null;
        var response = toResponse(result, serverTime, responseRefreshToken);

        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE) {
            headers.add(
                    HttpHeaders.SET_COOKIE,
                    RefreshTokenCookieHeader.create(result.refreshToken(), result.refreshTokenExpiresAt(), serverTime));
        }
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    private String selectCredential(LocalRefreshRequest request, HttpServletRequest servletRequest) {
        var cookieValues = refreshCookieValues(servletRequest);
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.RESPONSE_BODY) {
            if (!isNonBlank(request.refreshToken()) || !cookieValues.isEmpty()) {
                throw invalidCredentials();
            }
            return request.refreshToken();
        }
        if (request.refreshTokenDelivery() == RefreshTokenDelivery.HTTP_ONLY_COOKIE
                && request.refreshToken() == null
                && cookieValues.size() == 1
                && isNonBlank(cookieValues.getFirst())) {
            return cookieValues.getFirst();
        }
        throw invalidCredentials();
    }

    private java.util.List<String> refreshCookieValues(HttpServletRequest servletRequest) {
        var cookies = servletRequest.getCookies();
        if (cookies == null) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(cookies)
                .filter(cookie -> Objects.equals(REFRESH_COOKIE_NAME, cookie.getName()))
                .map(Cookie::getValue)
                .toList();
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private AppException invalidCredentials() {
        return new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
    }

    private LocalRefreshResponse toResponse(
            LocalRefreshResult result, java.time.Instant serverTime, String refreshToken) {
        return new LocalRefreshResponse(
                result.sessionId(),
                result.accessToken(),
                result.accessTokenExpiresAt(),
                result.refreshTokenExpiresAt(),
                serverTime,
                refreshToken);
    }
}
