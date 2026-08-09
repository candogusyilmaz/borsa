package dev.canverse.stocks.identity.configuration;

import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class LocalAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    static final String INVALID_TOKEN_DESCRIPTION = "The access token is invalid.";

    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, INVALID_TOKEN_DESCRIPTION, null);

    private final AccessTokenProperties properties;
    private final Clock clock;

    LocalAccessTokenValidator(AccessTokenProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        var observedAt = clock.instant();
        if (hasValidEnvelope(token, observedAt)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }

    private boolean hasValidEnvelope(Jwt token, Instant observedAt) {
        var issuedAt = token.getIssuedAt();
        var notBefore = token.getNotBefore();
        var expiresAt = token.getExpiresAt();
        return hasExpectedHeaders(token)
                && hasExpectedIssuerAndAudience(token)
                && hasCanonicalUuidClaim(token, "sub")
                && hasCanonicalUuidClaim(token, "jti")
                && hasCanonicalUuidClaim(token, "sid")
                && hasWholeSecondNumericDates(token)
                && isWholeSecond(issuedAt)
                && isWholeSecond(notBefore)
                && isWholeSecond(expiresAt)
                && issuedAt.equals(notBefore)
                && !notBefore.isAfter(observedAt)
                && expiresAt.isAfter(observedAt)
                && expiresAt.isAfter(issuedAt)
                && Duration.between(issuedAt, expiresAt).compareTo(properties.lifetime()) <= 0;
    }

    private boolean hasExpectedHeaders(Jwt token) {
        return "RS256".equals(token.getHeaders().get("alg"))
                && properties.keyId().equals(token.getHeaders().get("kid"))
                && "access".equals(token.getHeaders().get("typ"));
    }

    private boolean hasExpectedIssuerAndAudience(Jwt token) {
        var issuer = token.getIssuer();
        var audience = token.getAudience();
        return issuer != null
                && properties.issuer().toString().equals(issuer.toString())
                && audience != null
                && audience.size() == 1
                && properties.audience().equals(audience.getFirst());
    }

    private boolean hasCanonicalUuidClaim(Jwt token, String claimName) {
        var value = token.getClaims().get(claimName);
        if (!(value instanceof String stringValue)) {
            return false;
        }
        try {
            return UUID.fromString(stringValue).toString().equals(stringValue);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasWholeSecondNumericDates(Jwt token) {
        var tokenParts = token.getTokenValue().split("\\.", -1);
        if (tokenParts.length != 3) {
            return false;
        }
        try {
            var rawClaims = JSONObjectUtils.parse(new Base64URL(tokenParts[1]).decodeToString());
            return isWholeSecondNumericDate(rawClaims.get("iat"))
                    && isWholeSecondNumericDate(rawClaims.get("nbf"))
                    && isWholeSecondNumericDate(rawClaims.get("exp"));
        } catch (ParseException exception) {
            return false;
        }
    }

    private boolean isWholeSecondNumericDate(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isWholeSecond(Instant instant) {
        return instant != null && instant.getNano() == 0;
    }
}
