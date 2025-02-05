package dev.canverse.stocks.rest.authentication;

import dev.canverse.stocks.domain.entity.User;
import dev.canverse.stocks.service.authentication.TokenService;
import dev.canverse.stocks.service.authentication.UserService;
import dev.canverse.stocks.service.authentication.model.GoogleTokenRequest;
import dev.canverse.stocks.service.authentication.model.TokenCreateRequest;
import dev.canverse.stocks.service.authentication.model.TokenCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TokenController {
    private final TokenService tokenService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();

    @PostMapping("/token")
    public ResponseEntity<TokenCreateResponse> createAccessToken(@RequestBody TokenCreateRequest login) {
        var token = new UsernamePasswordAuthenticationToken(login.username(), login.password());
        var auth = authenticationManager.authenticate(token);
        var user = (User) auth.getPrincipal();

        var body = tokenService.createAccessToken(user);
        var cookie = tokenService.createRefreshTokenCookie(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(body);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<TokenCreateResponse> refreshAccessToken(@CookieValue(name = "refresh-token") Optional<String> refreshToken) {
        if (refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var user = tokenService.getSecurityUser(refreshToken.get());
        var body = tokenService.createAccessToken(user);
        var cookie = tokenService.createRefreshTokenCookie(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(body);
    }

    @PostMapping("/google")
    public ResponseEntity<TokenCreateResponse> googleLogin(@RequestBody GoogleTokenRequest request) {
        String idToken = request.token();

        try {
            var decodedToken = jwtDecoder.decode(idToken);
            String email = decodedToken.getClaim("email");
            String name = decodedToken.getClaim("name");
            String username = email.split("@")[0];

            var user = userService.loadUserByEmail(email)
                    .orElseGet(() -> userService.createUser(username, name, email, "!"));
            var body = tokenService.createAccessToken(user);
            var cookie = tokenService.createRefreshTokenCookie(user);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie)
                    .body(body);
        } catch (JwtException e) {
            throw new RuntimeException("Invalid Google token");
        }
    }
}