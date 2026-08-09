package dev.canverse.stocks.identity.application;

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

    @Transactional
    public LocalLoginResult login(String email, String rawPassword, String deviceLabel) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(rawPassword, "rawPassword");

        var userAccountId = passwordAuthenticationService.authenticate(email, rawPassword);
        var refreshSession = refreshSessionIssuanceService.issue(userAccountId, deviceLabel);
        var accessToken = accessTokenIssuanceService.issue(refreshSession.sessionId());

        return new LocalLoginResult(
                refreshSession.sessionId(),
                accessToken.accessToken(),
                accessToken.expiresAt(),
                refreshSession.refreshToken(),
                refreshSession.expiresAt());
    }
}
