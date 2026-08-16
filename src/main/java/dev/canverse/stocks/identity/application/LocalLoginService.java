package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.platform.application.SecurityEventRecorder;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocalLoginService {

    private final LocalPasswordAuthenticationService passwordAuthenticationService;
    private final RefreshSessionIssuanceService refreshSessionIssuanceService;
    private final AccessTokenIssuanceService accessTokenIssuanceService;
    private final SecurityEventRecorder securityEventRecorder;

    @Transactional
    public LocalLoginResult login(String email, String rawPassword, String deviceLabel) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(rawPassword, "rawPassword");

        var userAccountId = passwordAuthenticationService.authenticate(email, rawPassword);
        var refreshSession = refreshSessionIssuanceService.issue(userAccountId, deviceLabel);
        var accessToken = accessTokenIssuanceService.issue(refreshSession.sessionId());

        securityEventRecorder.record(
                userAccountId,
                SecurityEventRecorder.LOCAL_LOGIN_SUCCEEDED,
                Map.of(
                        "sessionId", refreshSession.sessionId().toString(),
                        "familyId", refreshSession.familyId().toString()));

        return new LocalLoginResult(
                refreshSession.sessionId(),
                accessToken.accessToken(),
                accessToken.expiresAt(),
                refreshSession.refreshToken(),
                refreshSession.expiresAt());
    }
}
