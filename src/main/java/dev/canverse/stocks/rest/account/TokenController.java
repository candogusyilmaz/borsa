package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.service.account.TokenService;
import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.GoogleTokenRequest;
import dev.canverse.stocks.service.account.model.TokenCreateRequest;
import dev.canverse.stocks.service.account.model.TokenCreateResponse;
import dev.canverse.stocks.service.account.model.UserRegistrationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TokenController {
    private final TokenService tokenService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public ResponseEntity<TokenCreateResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        var user = userService.register(request);

        return tokenService.create(user);
    }

    @PostMapping("/token")
    public ResponseEntity<TokenCreateResponse> createAccessToken(@RequestBody TokenCreateRequest login) {
        var token = new UsernamePasswordAuthenticationToken(login.username(), login.password());
        var auth = authenticationManager.authenticate(token);
        var user = (User) auth.getPrincipal();

        userService.updateLastLogin(user.getId());

        return tokenService.create(user);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenCreateResponse> refreshAccessToken(@CookieValue(name = "refresh-token") Optional<String> refreshToken) {
        if (refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var user = tokenService.getSecurityUser(refreshToken.get());

        return tokenService.create(user);
    }

    @PostMapping("/google")
    public ResponseEntity<TokenCreateResponse> googleLogin(@RequestBody GoogleTokenRequest request) {
        var idToken = request.token();

        try {
            var decodedToken = jwtDecoder.decode(idToken);
            var email = decodedToken.getClaimAsString("email");
            var name = decodedToken.getClaimAsString("name");

            var user = userService.loadUserByEmail(email)
                    .orElseGet(() -> userService.register(new UserRegistrationRequest(name, email, UUID.randomUUID().toString())));

            userService.updateLastLogin(user.getId());

            return tokenService.create(user);
        } catch (JwtException e) {
            throw new RuntimeException("Invalid Google token");
        }
    }
}