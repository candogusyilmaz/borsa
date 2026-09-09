package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.DeviceSessionQueryService;
import dev.canverse.stocks.identity.application.DeviceSessionRevocationService;
import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import dev.canverse.stocks.identity.web.request.LogoutRequest;
import dev.canverse.stocks.identity.web.request.LogoutScope;
import dev.canverse.stocks.identity.web.response.DeviceSessionResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DeviceSessionController {

    private final DeviceSessionQueryService queryService;
    private final DeviceSessionRevocationService revocationService;

    @PostMapping("logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedIdentity identity, @Valid @RequestBody LogoutRequest request) {
        if (request.scope() == LogoutScope.CURRENT_SESSION) {
            revocationService.logoutCurrentSession(identity.userAccountId(), identity.sessionId());
        } else if (request.scope() == LogoutScope.ALL_SESSIONS) {
            revocationService.logoutAllSessions(identity.userAccountId());
        }

        var headers = CacheHeaders.noStore();
        headers.add(HttpHeaders.SET_COOKIE, RefreshTokenCookieHeader.clear());
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    @GetMapping("sessions")
    public ResponseEntity<List<DeviceSessionResponse>> listSessions(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        var response = queryService.listSessions(identity.userAccountId(), identity.sessionId());

        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }

    @GetMapping("sessions/{familyId}")
    public ResponseEntity<DeviceSessionResponse> getSession(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID familyId) {
        var response = queryService.getSessionDetail(identity.userAccountId(), identity.sessionId(), familyId);

        var headers = CacheHeaders.noStore();
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    @DeleteMapping("sessions/{familyId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID familyId) {
        var isCurrentFamily = revocationService.revokeSelectedFamily(identity.userAccountId(), identity.sessionId(), familyId);

        var headers = CacheHeaders.noStore();
        if (isCurrentFamily) {
            headers.add(HttpHeaders.SET_COOKIE, RefreshTokenCookieHeader.clear());
        }
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }
}
