package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.AuthenticatedIdentityResolver;
import dev.canverse.stocks.identity.application.CurrentUserQueryService;
import dev.canverse.stocks.identity.web.response.CurrentUserResponse;
import dev.canverse.stocks.platform.web.CacheHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CurrentUserController {

    private final AuthenticatedIdentityResolver identityResolver;
    private final CurrentUserQueryService currentUserQueryService;

    @GetMapping("me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        var identity = identityResolver.resolve(authentication);
        var response = currentUserQueryService.getCurrentUser(identity.userAccountId());

        return new ResponseEntity<>(response, CacheHeaders.noStore(), HttpStatus.OK);
    }
}
