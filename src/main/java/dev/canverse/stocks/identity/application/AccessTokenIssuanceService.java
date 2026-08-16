package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.configuration.AccessTokenProperties;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessTokenIssuanceService {

    private final DeviceSessionRepository deviceSessionRepository;
    private final JwtEncoder jwtEncoder;
    private final AccessTokenProperties accessTokenProperties;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @Transactional(readOnly = true)
    public IssuedAccessToken issue(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");

        var observedAt = clock.instant();
        var issuedAt = observedAt.truncatedTo(ChronoUnit.SECONDS);
        var deviceSession = deviceSessionRepository
                .findById(sessionId)
                .filter(session -> session.isActiveAndUserEnabled(observedAt))
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS));

        var configuredExpiresAt = observedAt.plus(accessTokenProperties.lifetime());
        var expiresAt =
                earlier(configuredExpiresAt, deviceSession.getExpiresAt()).truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(issuedAt)) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var tokenId = idGenerator.next();
        var headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(accessTokenProperties.keyId())
                .type("access")
                .build();
        var claims = JwtClaimsSet.builder()
                .issuer(accessTokenProperties.issuer().toString())
                .subject(deviceSession.getUserAccount().getId().toString())
                .audience(List.of(accessTokenProperties.audience()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId.toString())
                .claim("sid", deviceSession.getId().toString())
                .build();
        var encodedToken = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims));

        return new IssuedAccessToken(encodedToken.getTokenValue(), expiresAt);
    }

    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
