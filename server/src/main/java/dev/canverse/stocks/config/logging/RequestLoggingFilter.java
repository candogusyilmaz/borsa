package dev.canverse.stocks.config.logging;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;

import dev.canverse.stocks.domain.entity.account.User;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    static final String USER_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".USER_ID";

    private static final Logger HTTP_LOG =
            LoggerFactory.getLogger("dev.canverse.stocks.config.logging.HTTP");

    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Pattern UUID_SEGMENT =
            Pattern.compile(
                    "(?<=/)([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?=/|$)");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final int MAX_ROUTE_LENGTH = 128;

    private static final Set<String> STATIC_EXTENSIONS =
            Set.of(
                    ".js", ".css", ".map", ".svg", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".woff",
                    ".woff2", ".ttf", ".eot", ".webp", ".html");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.REQUEST;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previousRequestId = MDC.get(MDC_REQUEST_ID);
        String previousUserId = MDC.get(MDC_USER_ID);
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startNanos = System.nanoTime();
        Throwable unhandled = null;

        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            unhandled = ex;
            throw ex;
        } finally {
            try {
                String userId = resolveEffectiveUserId(request);
                if (userId != null) {
                    MDC.put(MDC_USER_ID, userId);
                } else {
                    MDC.remove(MDC_USER_ID);
                }

                int status = resolveStatus(response, unhandled);
                long durationMs = elapsedMs(startNanos);
                String route = resolveRoute(request);

                if (!shouldSkipLogging(request)) {
                    logCompletion(
                            completionBuilder(status),
                            request.getMethod(),
                            route,
                            status,
                            durationMs);
                } else if (HTTP_LOG.isTraceEnabled()) {
                    logCompletion(
                            HTTP_LOG.atTrace(), request.getMethod(), route, status, durationMs);
                }
            } finally {
                restoreMdc(MDC_REQUEST_ID, previousRequestId);
                restoreMdc(MDC_USER_ID, previousUserId);
            }
        }
    }

    private LoggingEventBuilder completionBuilder(int status) {
        if (status >= 500) {
            return HTTP_LOG.atError();
        }
        if (status >= 400) {
            return HTTP_LOG.atWarn();
        }
        return HTTP_LOG.atInfo();
    }

    private void logCompletion(
            LoggingEventBuilder builder, String method, String route, int status, long durationMs) {
        builder.addKeyValue("event", "http_request_completed")
                .addKeyValue("method", method)
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .log("{} {} {} {}ms", method, route, status, durationMs);
    }

    static String resolveRequestId(String headerValue) {
        if (headerValue != null) {
            String trimmed = headerValue.trim();
            if (VALID_REQUEST_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return String.format("%08x", ThreadLocalRandom.current().nextInt());
    }

    static String extractAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof User user && user.getId() != null) {
            return String.valueOf(user.getId());
        }
        return null;
    }

    static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    private String resolveEffectiveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(USER_ID_ATTRIBUTE);
        if (attr instanceof String userId && !userId.isBlank()) {
            return userId;
        }
        return extractAuthenticatedUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    private int resolveStatus(HttpServletResponse response, Throwable unhandled) {
        int status = response.getStatus();
        if (unhandled != null && status < 400) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return status;
    }

    private String resolveRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            String template = pattern.toString();
            if (!template.isBlank()) {
                return sanitizeRoute(template);
            }
        }

        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }

        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        String sanitized = sanitizeRoute(uri);
        sanitized = UUID_SEGMENT.matcher(sanitized).replaceAll("{id}");
        sanitized = NUMERIC_SEGMENT.matcher(sanitized).replaceAll("{id}");
        return sanitized;
    }

    private String sanitizeRoute(String raw) {
        String cleaned = CONTROL_CHARS.matcher(raw).replaceAll("");
        if (cleaned.length() > MAX_ROUTE_LENGTH) {
            return cleaned.substring(0, MAX_ROUTE_LENGTH);
        }
        return cleaned.isEmpty() ? "/" : cleaned;
    }

    private boolean shouldSkipLogging(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }

        String normalized = uri.toLowerCase(Locale.ROOT);
        if (normalized.equals("/actuator")
                || normalized.startsWith("/actuator/")
                || normalized.equals("/swagger-ui.html")
                || normalized.startsWith("/swagger-ui/")
                || normalized.equals("/v3/api-docs")
                || normalized.startsWith("/v3/api-docs/")
                || normalized.equals("/favicon.ico")
                || normalized.equals("/robots.txt")
                || normalized.startsWith("/assets/")) {
            return true;
        }

        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot > normalized.lastIndexOf('/')) {
            String ext = normalized.substring(lastDot);
            return STATIC_EXTENSIONS.contains(ext);
        }

        return false;
    }
}
