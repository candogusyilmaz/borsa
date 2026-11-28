package dev.canverse.stocks.rest.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.rest.ExceptionHandlerAdvice;
import dev.canverse.stocks.service.account.GoogleAuthService;
import dev.canverse.stocks.service.account.TokenService;
import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.TokenCreateResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TokenRefreshTests {

    private TokenService tokenService;
    private MockMvc mockMvc;
    private Logger controllerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        var userService = mock(UserService.class);
        var googleAuthService = mock(GoogleAuthService.class);
        var authenticationManager = mock(AuthenticationManager.class);

        var controller = new TokenController(tokenService, userService, googleAuthService, authenticationManager);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ExceptionHandlerAdvice()).build();

        controllerLogger = (Logger) LoggerFactory.getLogger(TokenController.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        controllerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(logAppender);
    }

    @Test
    void returnsUnauthorizedWhenRefreshCookieIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")).andExpect(status().isUnauthorized());

        assertThat(logAppender.list).hasSize(1);
        var event = logAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(toMap(event)).containsEntry("event", "authentication_failed")
            .containsEntry("authMethod", "refresh_token")
            .containsEntry("reason", "missing_token");
    }

    @Test
    void returnsUnauthorizedWhenRefreshCookieIsBlank() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", "   "))).andExpect(status().isUnauthorized());

        assertThat(logAppender.list).hasSize(1);
        assertThat(toMap(logAppender.list.getFirst())).containsEntry("reason", "missing_token");
    }

    @Test
    void returnsUnauthorizedWhenRefreshTokenIsExpired() throws Exception {
        var expiredError = new OAuth2Error("invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null);
        when(tokenService.getSecurityUser("expired-secret-jwt")).thenThrow(new JwtValidationException("Expired", List.of(expiredError)));

        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", "expired-secret-jwt")))
            .andExpect(status().isUnauthorized());

        assertThat(logAppender.list).hasSize(1);
        var event = logAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(toMap(event)).containsEntry("event", "authentication_failed")
            .containsEntry("authMethod", "refresh_token")
            .containsEntry("reason", "expired_token");
        assertThat(event.getFormattedMessage()).doesNotContain("expired-secret-jwt");
    }

    @Test
    void returnsUnauthorizedWhenRefreshTokenIsMalformedOrInvalidSignature() throws Exception {
        when(tokenService.getSecurityUser("malformed-jwt")).thenThrow(new BadJwtException("Invalid signature"));

        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", "malformed-jwt")))
            .andExpect(status().isUnauthorized());

        assertThat(logAppender.list).hasSize(1);
        assertThat(toMap(logAppender.list.getFirst())).containsEntry("event", "authentication_failed")
            .containsEntry("authMethod", "refresh_token")
            .containsEntry("reason", "invalid_token");
    }

    @Test
    void returnsUnauthorizedWhenRefreshTokenUserDoesNotExist() throws Exception {
        when(tokenService.getSecurityUser("orphaned-jwt")).thenThrow(new UsernameNotFoundException("User not found."));

        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", "orphaned-jwt")))
            .andExpect(status().isUnauthorized());

        assertThat(logAppender.list).hasSize(1);
        assertThat(toMap(logAppender.list.getFirst())).containsEntry("event", "authentication_failed")
            .containsEntry("authMethod", "refresh_token")
            .containsEntry("reason", "user_not_found");
    }

    @Test
    void returnsOkAndNewSessionWhenRefreshTokenIsValid() throws Exception {
        var user = new User("user@example.com", "hash");
        user.setId(7L);
        user.setName("Can");

        when(tokenService.getSecurityUser("valid-jwt")).thenReturn(user);
        when(tokenService.create(user)).thenReturn(ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, "refresh-token=next-jwt; HttpOnly")
            .body(new TokenCreateResponse("Can", "user@example.com", "access-jwt", List.of())));

        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie("refresh-token", "valid-jwt")))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.token").value("access-jwt"));
    }

    @Test
    void tokenServiceThrowsBadJwtWhenSubjectMissing() {
        var userDetailsService = mock(UserDetailsService.class);
        var jwtEncoder = mock(JwtEncoder.class);
        var jwtDecoder = mock(JwtDecoder.class);
        var jwt = Jwt.withTokenValue("tok")
            .header("alg", "RS256")
            .claim("iss", "finance-api")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        when(jwtDecoder.decode("tok")).thenReturn(jwt);

        var service = new TokenService(userDetailsService, jwtEncoder, jwtDecoder);

        assertThatThrownBy(() -> service.getSecurityUser("tok")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void logoutReturnsNoContentAndClearsRefreshTokenCookie() throws Exception {
        var clearCookie = "refresh-token=; Path=/; Max-Age=0; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=none";
        when(tokenService.clearRefreshTokenCookie()).thenReturn(clearCookie);

        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, clearCookie));
    }

    @Test
    void clearRefreshTokenCookiePreservesCookieAttributesWithZeroMaxAge() {
        var service = new TokenService(mock(UserDetailsService.class), mock(JwtEncoder.class), mock(JwtDecoder.class));

        var cookie = service.clearRefreshTokenCookie();

        assertThat(cookie).startsWith("refresh-token=;")
            .contains("Path=/")
            .contains("Max-Age=0")
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=none");
    }

    private static Map<String, Object> toMap(ILoggingEvent event) {
        if (event.getKeyValuePairs() == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        for (var kv : event.getKeyValuePairs()) {
            map.put(kv.key, kv.value);
        }
        return map;
    }
}
