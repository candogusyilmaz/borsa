package dev.canverse.stocks.rest.authentication;

import dev.canverse.stocks.domain.entity.User;
import dev.canverse.stocks.service.authentication.TokenService;
import dev.canverse.stocks.service.authentication.model.TokenCreateRequest;
import dev.canverse.stocks.service.authentication.model.TokenCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TokenController {
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;

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
}