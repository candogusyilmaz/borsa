package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.AuthenticatedIdentityResolver;
import dev.canverse.stocks.identity.application.DeviceSessionRevocationService;
import dev.canverse.stocks.identity.web.request.LogoutRequest;
import dev.canverse.stocks.identity.web.request.LogoutScope;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LocalLogoutController {

    private final AuthenticatedIdentityResolver identityResolver;
    private final DeviceSessionRevocationService revocationService;

    @PostMapping("logout")
    public ResponseEntity<Void> logout(Authentication authentication, @Valid @RequestBody LogoutRequest request) {
        var identity = identityResolver.resolve(authentication);

        if (request.scope() == LogoutScope.CURRENT_SESSION) {
            revocationService.logoutCurrentSession(identity.userAccountId(), identity.sessionId());
        } else if (request.scope() == LogoutScope.ALL_SESSIONS) {
            revocationService.logoutAllSessions(identity.userAccountId());
        }

        var headers = CacheHeaders.noStore();
        headers.add(HttpHeaders.SET_COOKIE, RefreshTokenCookieHeader.clear());
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }
}
