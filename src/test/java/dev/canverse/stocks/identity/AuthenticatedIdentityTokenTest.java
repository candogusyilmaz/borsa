package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.application.AuthenticatedIdentityToken;
import dev.canverse.stocks.identity.application.model.AuthenticatedIdentity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedIdentityTokenTest {

    @Test
    void exposesTypedIdentityJwtCredentialsAndStableName() {
        var userAccountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        var sessionId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        var jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", userAccountId.toString())
                .claim("sid", sessionId.toString())
                .build();

        var token = new AuthenticatedIdentityToken(new AuthenticatedIdentity(userAccountId, sessionId), jwt);

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getPrincipal()).isEqualTo(new AuthenticatedIdentity(userAccountId, sessionId));
        assertThat(token.getCredentials()).isSameAs(jwt);
        assertThat(token.getName()).isEqualTo(userAccountId.toString());
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void requiresIdentityAndJwtCredentials() {
        var jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "subject")
                .build();
        var identity = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> new AuthenticatedIdentityToken(null, jwt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("principal");
        assertThatThrownBy(() -> new AuthenticatedIdentityToken(identity, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("credentials");
    }
}
