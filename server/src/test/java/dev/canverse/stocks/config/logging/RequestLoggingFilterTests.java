package dev.canverse.stocks.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import dev.canverse.stocks.config.JwtAuthenticationConverter;
import dev.canverse.stocks.config.SecurityConfiguration;
import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.rest.ExceptionHandlerAdvice;

import jakarta.servlet.ServletException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@WebMvcTest(controllers = RequestLoggingFilterTests.TestController.class)
@Import({
    SecurityConfiguration.class,
    LoggingConfiguration.class,
    ExceptionHandlerAdvice.class,
    RequestLoggingFilterTests.TestController.class
})
class RequestLoggingFilterTests {

    @Autowired private WebApplicationContext context;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private JwtAuthenticationConverter jwtAuthenticationConverter;
    @MockitoBean private UserDetailsService userDetailsService;

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> httpAppender;
    private ListAppender<ILoggingEvent> adviceAppender;
    private ListAppender<ILoggingEvent> controllerAppender;

    private Logger httpLogger;
    private Logger adviceLogger;
    private Logger controllerLogger;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(new RequestLoggingFilter())
                        .apply(springSecurity())
                        .build();

        httpLogger = (Logger) LoggerFactory.getLogger("dev.canverse.stocks.config.logging.HTTP");
        adviceLogger = (Logger) LoggerFactory.getLogger(ExceptionHandlerAdvice.class);
        controllerLogger = (Logger) LoggerFactory.getLogger(TestController.class);

        httpAppender = attachAppender(httpLogger);
        adviceAppender = attachAppender(adviceLogger);
        controllerAppender = attachAppender(controllerLogger);

        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        httpLogger.detachAppender(httpAppender);
        adviceLogger.detachAppender(adviceAppender);
        controllerLogger.detachAppender(controllerAppender);
        MDC.clear();
    }

    @Test
    void logsAuthenticatedRequestWithNormalizedRouteTrustedUserIdAndResponseHeader()
            throws Exception {
        var user = testUser(42L, "private-user@example.com");
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        var result =
                mockMvc.perform(
                                get("/api/test/portfolios/98765")
                                        .queryParam("token", "secret-query-value")
                                        .header("X-User-Id", "spoofed-user")
                                        .with(authentication(auth)))
                        .andExpect(status().isOk())
                        .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER))
                        .andReturn();

        String responseRequestId =
                result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(responseRequestId).matches("^[0-9a-f]{8}$");

        // Controller log during request received both requestId and trusted internal userId
        assertThat(controllerAppender.list).hasSize(1);
        var controllerMdc = controllerAppender.list.getFirst().getMDCPropertyMap();
        assertThat(controllerMdc.get("requestId")).isEqualTo(responseRequestId);
        assertThat(controllerMdc.get("userId")).isEqualTo("42");

        // Exactly one HTTP completion log with normalized route template and no query params/email
        assertThat(httpAppender.list).hasSize(1);
        var event = httpAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap().get("requestId")).isEqualTo(responseRequestId);
        assertThat(event.getMDCPropertyMap().get("userId")).isEqualTo("42");
        assertThat(event.getFormattedMessage())
                .startsWith("GET /api/test/portfolios/{portfolioId} 200 ");
        assertThat(event.getFormattedMessage()).doesNotContain("98765");
        assertThat(event.getFormattedMessage()).doesNotContain("secret-query-value");
        assertThat(event.getFormattedMessage()).doesNotContain("private-user@example.com");
        assertThat(event.getFormattedMessage()).doesNotContain("spoofed-user");

        var kv = toMap(event);
        assertThat(kv.get("event")).isEqualTo("http_request_completed");
        assertThat(kv.get("method")).isEqualTo("GET");
        assertThat(kv.get("route")).isEqualTo("/api/test/portfolios/{portfolioId}");
        assertThat(kv.get("status")).isEqualTo(200);
        assertThat((Long) kv.get("durationMs")).isGreaterThanOrEqualTo(0L);

        // MDC cleaned up after request completion
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void logsAnonymousPublicRequestWithoutUserIdAndAcceptsValidExternalRequestId()
            throws Exception {
        mockMvc.perform(
                        post("/api/auth/token")
                                .header(RequestLoggingFilter.REQUEST_ID_HEADER, "req-abc12345")
                                .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "req-abc12345"));

        assertThat(httpAppender.list).hasSize(1);
        var event = httpAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap().get("requestId")).isEqualTo("req-abc12345");
        assertThat(event.getMDCPropertyMap().get("userId")).isNull();
        assertThat(toMap(event).get("route")).isEqualTo("/api/auth/token");
        assertThat(toMap(event).get("status")).isEqualTo(200);
    }

    @Test
    void rejectsMaliciousOrInvalidRequestIdHeaderAndGeneratesSafeReplacement() throws Exception {
        var result =
                mockMvc.perform(
                                post("/api/auth/token")
                                        .header(
                                                RequestLoggingFilter.REQUEST_ID_HEADER,
                                                "bad\r\nFAKE LOG LINE"))
                        .andExpect(status().isOk())
                        .andReturn();

        String effectiveId = result.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(effectiveId).matches("^[0-9a-f]{8}$");
        assertThat(httpAppender.list).hasSize(1);
        assertThat(httpAppender.list.getFirst().getMDCPropertyMap().get("requestId"))
                .isEqualTo(effectiveId);
    }

    @Test
    void logsSecurityRejectedUnauthenticatedRequestWithNormalizedNumericPath() throws Exception {
        mockMvc.perform(get("/api/test/portfolios/12345?secret=topsecret"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));

        assertThat(httpAppender.list).hasSize(1);
        var event = httpAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap().get("userId")).isNull();
        var kv = toMap(event);
        assertThat(kv.get("status")).isEqualTo(401);
        assertThat(kv.get("route")).isEqualTo("/api/test/portfolios/{id}");
        assertThat(event.getFormattedMessage()).doesNotContain("topsecret");
        assertThat(event.getFormattedMessage()).doesNotContain("12345");
    }

    @Test
    void logsBadCredentialsWithoutStackTraceOrCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."));

        assertThat(adviceAppender.list).hasSize(1);
        var adviceEvent = adviceAppender.list.getFirst();
        assertThat(adviceEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(adviceEvent.getThrowableProxy()).isNull();
        assertThat(toMap(adviceEvent).get("event")).isEqualTo("authentication_failed");
        assertThat(toMap(adviceEvent).get("reason")).isEqualTo("bad_credentials");

        assertThat(httpAppender.list).hasSize(1);
        assertThat(httpAppender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(toMap(httpAppender.list.getFirst()).get("status")).isEqualTo(401);
    }

    @Test
    void logsAuthorizationFailureForAuthenticatedUserWithUserIdAndNoStackTrace() throws Exception {
        var user = testUser(77L, "member@example.com");
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        mockMvc.perform(get("/api/test/admin-only").with(authentication(auth)))
                .andExpect(status().isForbidden());

        assertThat(adviceAppender.list).hasSize(1);
        var adviceEvent = adviceAppender.list.getFirst();
        assertThat(adviceEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(adviceEvent.getThrowableProxy()).isNull();
        assertThat(adviceEvent.getMDCPropertyMap().get("userId")).isEqualTo("77");
        assertThat(toMap(adviceEvent).get("event")).isEqualTo("authorization_failed");

        assertThat(httpAppender.list).hasSize(1);
        var httpEvent = httpAppender.list.getFirst();
        assertThat(httpEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(httpEvent.getMDCPropertyMap().get("userId")).isEqualTo("77");
        assertThat(toMap(httpEvent).get("status")).isEqualTo(403);
    }

    @Test
    void doesNotLogValidationFailureOrNotFoundAsErrorWithStackTrace() throws Exception {
        var user = testUser(11L, "user@example.com");
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        mockMvc.perform(
                        post("/api/test/validate")
                                .with(authentication(auth))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalid-params[0].field").value("name"));

        assertThat(adviceAppender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
        assertThat(httpAppender.list).hasSize(1);
        assertThat(httpAppender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(toMap(httpAppender.list.getFirst()).get("status")).isEqualTo(400);

        httpAppender.list.clear();
        adviceAppender.list.clear();

        mockMvc.perform(get("/api/test/not-found").with(authentication(auth)))
                .andExpect(status().isNotFound());

        assertThat(adviceAppender.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
        assertThat(httpAppender.list).hasSize(1);
        assertThat(toMap(httpAppender.list.getFirst()).get("status")).isEqualTo(404);
    }

    @Test
    void logsBusinessRuleViolationAtWarnWithoutStackTraceAndServerErrorAtErrorWithStackTrace()
            throws Exception {
        var user = testUser(12L, "user@example.com");
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        mockMvc.perform(get("/api/test/business-rule").with(authentication(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Not enough quantity"));

        assertThat(adviceAppender.list).hasSize(1);
        var businessEvent = adviceAppender.list.getFirst();
        assertThat(businessEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(businessEvent.getThrowableProxy()).isNull();
        assertThat(toMap(businessEvent).get("event")).isEqualTo("business_rule_violation");

        adviceAppender.list.clear();
        httpAppender.list.clear();

        mockMvc.perform(get("/api/test/db-failure").with(authentication(auth)))
                .andExpect(status().isInternalServerError());

        assertThat(adviceAppender.list).hasSize(1);
        var serverEvent = adviceAppender.list.getFirst();
        assertThat(serverEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(serverEvent.getThrowableProxy()).isNotNull();
        assertThat(toMap(serverEvent).get("event")).isEqualTo("unhandled_exception");
        assertThat(toMap(serverEvent).get("category")).isEqualTo("database");

        assertThat(httpAppender.list).hasSize(1);
        assertThat(httpAppender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(httpAppender.list.getFirst().getThrowableProxy()).isNull();
        assertThat(toMap(httpAppender.list.getFirst()).get("status")).isEqualTo(500);
    }

    @Test
    void excludesActuatorPreflightAndStaticInfrastructureRequests() throws Exception {
        mockMvc.perform(get("/actuator/health"));
        mockMvc.perform(get("/actuator/prometheus"));
        mockMvc.perform(get("/assets/index-abc123.js"));
        mockMvc.perform(get("/favicon.ico"));
        mockMvc.perform(get("/swagger-ui/index.html"));
        mockMvc.perform(
                        options("/api/test/portfolios/1")
                                .header(HttpHeaders.ORIGIN, "http://localhost:5173/")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());

        assertThat(httpAppender.list).isEmpty();
    }

    @Test
    void preservesUnrelatedMdcKeysAndCleansUpAfterUnhandledFilterException() {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        var filter = new RequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/api/test/boom/55");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request,
                                        response,
                                        (req, res) -> {
                                            throw new ServletException("boom");
                                        }))
                .isInstanceOf(ServletException.class);

        assertThat(httpAppender.list).hasSize(1);
        var event = httpAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(toMap(event).get("status")).isEqualTo(500);
        assertThat(toMap(event).get("route")).isEqualTo("/api/test/boom/{id}");
        assertThat(event.getMDCPropertyMap().get("traceId"))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

        // Request MDC removed, unrelated traceId preserved
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("traceId")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void mdcTaskDecoratorPropagatesAndRestoresContextAcrossThreads() throws Exception {
        MDC.put("requestId", "a83f2b91");
        MDC.put("userId", "42");
        MDC.put("traceId", "trace-123");

        var decorator = new MdcTaskDecorator();
        var captured = new AtomicReference<Map<String, String>>();
        Runnable decorated =
                decorator.decorate(
                        () -> {
                            captured.set(MDC.getCopyOfContextMap());
                            MDC.put("requestId", "mutated");
                        });

        MDC.clear();

        var workerAfter = new AtomicReference<Map<String, String>>();
        var worker =
                new Thread(
                        () -> {
                            MDC.put("workerKey", "existing");
                            decorated.run();
                            workerAfter.set(MDC.getCopyOfContextMap());
                            MDC.clear();
                        });
        worker.start();
        worker.join();

        assertThat(captured.get())
                .containsEntry("requestId", "a83f2b91")
                .containsEntry("userId", "42")
                .containsEntry("traceId", "trace-123");
        assertThat(workerAfter.get()).containsOnly(Map.entry("workerKey", "existing"));
    }

    @Test
    void doesNotLogTwiceOnErrorDispatch() throws Exception {
        var filter = new RequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(httpAppender.list).isEmpty();
    }

    private static ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static Map<String, Object> toMap(ILoggingEvent event) {
        if (event.getKeyValuePairs() == null) {
            return Map.of();
        }
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }

    private static User testUser(Long id, String email) {
        var user = new User(email, "hashed-password");
        user.setId(id);
        user.setName("Test User");
        return user;
    }

    @RestController
    @RequestMapping("/api")
    static class TestController {
        private static final org.slf4j.Logger log = LoggerFactory.getLogger(TestController.class);

        @GetMapping("/test/portfolios/{portfolioId}")
        String getPortfolio(@PathVariable long portfolioId) {
            log.atInfo().addKeyValue("portfolioId", portfolioId).log("Loaded portfolio");
            return "ok";
        }

        @PostMapping("/auth/token")
        String publicLogin() {
            return "ok";
        }

        @PostMapping("/auth/register")
        String badCredentials() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "secret";
        }

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody ValidationPayload payload) {
            return payload.name();
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("Portfolio not found");
        }

        @GetMapping("/test/business-rule")
        String businessRule() {
            throw new IllegalArgumentException("Not enough quantity");
        }

        @GetMapping("/test/db-failure")
        String dbFailure() {
            throw new DataAccessResourceFailureException("Connection refused");
        }

        record ValidationPayload(@NotBlank String name) {}
    }
}
