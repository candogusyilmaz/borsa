package dev.canverse.stocks.identity.web;

import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class LocalBearerAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNRESOLVED_MESSAGE = "The authentication failure could not be resolved.";

    private final HandlerExceptionResolver handlerExceptionResolver;

    public LocalBearerAuthenticationEntryPoint(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws ServletException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        var resolved = handlerExceptionResolver.resolveException(request, response, null, new AppException(IdentityErrorCode.INVALID_CREDENTIALS));
        if (resolved == null) {
            throw new ServletException(UNRESOLVED_MESSAGE);
        }
    }
}
