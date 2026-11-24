package dev.canverse.stocks.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousUserId = MDC.get(RequestLoggingFilter.MDC_USER_ID);
        String userId =
                RequestLoggingFilter.extractAuthenticatedUserId(
                        SecurityContextHolder.getContext().getAuthentication());

        if (userId != null) {
            MDC.put(RequestLoggingFilter.MDC_USER_ID, userId);
            request.setAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE, userId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestLoggingFilter.restoreMdc(RequestLoggingFilter.MDC_USER_ID, previousUserId);
        }
    }
}
