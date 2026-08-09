package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.input.RegistrationRequest;
import dev.canverse.stocks.identity.output.RegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LocalAccountRegistrationController {

    private final LocalAccountRegistrationService registrationService;

    public LocalAccountRegistrationController(LocalAccountRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        var userId = registrationService.register(request.email(), request.password());
        return new RegistrationResponse(userId);
    }
}
