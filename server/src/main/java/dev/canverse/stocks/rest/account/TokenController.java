package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.service.account.GoogleAuthService;
import dev.canverse.stocks.service.account.TokenService;
import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.GoogleTokenRequest;
import dev.canverse.stocks.service.account.model.TokenCreateRequest;
import dev.canverse.stocks.service.account.model.TokenCreateResponse;
import dev.canverse.stocks.service.account.model.UserRegistrationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TokenController {
    private final TokenService tokenService;
    private final UserService userService;
    private final GoogleAuthService googleAuthService;
    private final AuthenticationManager authenticationManager;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public ResponseEntity<@NotNull TokenCreateResponse> register(
            @Valid @RequestBody UserRegistrationRequest request) {
        var user = userService.register(request);

        return tokenService.create(user);
    }

    @PostMapping("/token")
    public ResponseEntity<@NotNull TokenCreateResponse> createAccessToken(
            @RequestBody TokenCreateRequest login) {
        var token = new UsernamePasswordAuthenticationToken(login.username(), login.password());
        var auth = authenticationManager.authenticate(token);
        var user = (User) auth.getPrincipal();

        userService.updateLastLogin(user.getId());
        var response = tokenService.create(user);

        log.atInfo()
                .addKeyValue("event", "authentication_succeeded")
                .addKeyValue("authMethod", "password")
                .addKeyValue("userId", String.valueOf(user.getId()))
                .log("Authentication succeeded");

        return response;
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<@NotNull TokenCreateResponse> refreshAccessToken(
            @CookieValue(name = "refresh-token") Optional<String> refreshToken) {
        if (refreshToken.isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "authentication_failed")
                    .addKeyValue("authMethod", "refresh_token")
                    .addKeyValue("reason", "missing_token")
                    .log("Token refresh failed");
            return ResponseEntity.badRequest().build();
        }

        var user = tokenService.getSecurityUser(refreshToken.get());
        var response = tokenService.create(user);

        log.atInfo()
                .addKeyValue("event", "authentication_succeeded")
                .addKeyValue("authMethod", "refresh_token")
                .addKeyValue("userId", String.valueOf(user.getId()))
                .log("Token refreshed");

        return response;
    }

    @PostMapping("/google")
    public ResponseEntity<@NotNull TokenCreateResponse> googleLogin(
            @Valid @RequestBody GoogleTokenRequest request) {
        var googleUser = googleAuthService.verifyAuthorizationCode(request.code());

        var user =
                userService
                        .loadUserByEmail(googleUser.email())
                        .orElseGet(
                                () ->
                                        userService.register(
                                                new UserRegistrationRequest(
                                                        googleUser.name(),
                                                        googleUser.email(),
                                                        UUID.randomUUID().toString())));

        userService.updateLastLogin(user.getId());
        var response = tokenService.create(user);

        log.atInfo()
                .addKeyValue("event", "authentication_succeeded")
                .addKeyValue("authMethod", "google")
                .addKeyValue("userId", String.valueOf(user.getId()))
                .log("Authentication succeeded");

        return response;
    }
}
