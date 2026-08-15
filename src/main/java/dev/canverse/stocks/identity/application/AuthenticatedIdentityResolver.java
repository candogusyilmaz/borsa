package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedIdentityResolver {

    public AuthenticatedIdentity resolve(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)
                || !authentication.isAuthenticated()) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var jwt = jwtAuthenticationToken.getToken();
        if (jwt == null) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var subClaim = jwt.getClaims().get("sub");
        var sidClaim = jwt.getClaims().get("sid");
        var userAccountId = parseCanonicalUuid(subClaim);
        var sessionId = parseCanonicalUuid(sidClaim);

        if (!Objects.equals(authentication.getName(), userAccountId.toString())) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        return new AuthenticatedIdentity(userAccountId, sessionId);
    }

    private UUID parseCanonicalUuid(Object claim) {
        if (!(claim instanceof String value)) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        try {
            var parsed = UUID.fromString(value);
            if (parsed.toString().equals(value)) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // Handled below
        }
        throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
    }
}
