package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.application.LocalRegistrationAttemptService;
import dev.canverse.stocks.identity.input.RegistrationRequest;
import dev.canverse.stocks.identity.output.RegistrationResponse;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
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

    private final LocalRegistrationAttemptService registrationAttemptService;

    public LocalAccountRegistrationController(LocalRegistrationAttemptService registrationAttemptService) {
        this.registrationAttemptService = registrationAttemptService;
    }

    @PostMapping("register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(
            @Valid @RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        var remoteAddr = servletRequest.getRemoteAddr();
        var traceId = (String) servletRequest.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE);
        if (traceId == null) {
            traceId = "unknown";
        }

        var userId = registrationAttemptService.attemptRegistration(
                request.email(), request.password(), remoteAddr, traceId);
        return new RegistrationResponse(userId);
    }
}
