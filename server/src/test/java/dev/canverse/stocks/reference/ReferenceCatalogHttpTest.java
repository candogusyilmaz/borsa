package dev.canverse.stocks.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=2h", "stocks.identity.access-token.issuer=https://issuer.test",
                "stocks.identity.access-token.audience=canverse-test-api", "stocks.identity.access-token.lifetime=5m",
                "stocks.identity.access-token.key-id=test-ephemeral"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
@Import(ReferenceCatalogHttpTest.TestOverrides.class)
class ReferenceCatalogHttpTest {

    private static final UUID XIST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant T0 = Instant.parse("2026-08-15T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LocalAccountRegistrationService registrationService;

    @Autowired
    RefreshSessionIssuanceService sessionIssuanceService;

    @Autowired
    AccessTokenIssuanceService tokenIssuanceService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE reference.instrument_alias, reference.instrument, reference.market_calendar," +
                " platform.security_event, identity.device_session, identity.auth_identity," + " identity.user_account CASCADE");
    }

    @Test
    void returnsExactOfflineSeedsWithStableOrderingAndNoFinancialClaims() throws Exception {
        var identity = authenticated("catalog-seeds@example.com");

        var countries = mockMvc.perform(get("/api/v1/reference/countries").header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().json("""
                        [
                          {"code":"GB","name":"United Kingdom","active":true},
                          {"code":"TR","name":"Türkiye","active":true},
                          {"code":"US","name":"United States","active":true}
                        ]
                        """, true)).andReturn();
        assertNoSession(countries);

        var currencies = mockMvc.perform(get("/api/v1/reference/currencies").header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().json("""
                        [
                          {"code":"EUR","name":"Euro","symbol":"€","minorUnit":2,"active":true},
                          {"code":"GBP","name":"Pound sterling","symbol":"£","minorUnit":2,"active":true},
                          {"code":"TRY","name":"Turkish lira","symbol":"₺","minorUnit":2,"active":true},
                          {"code":"USD","name":"United States dollar","symbol":"$","minorUnit":2,"active":true}
                        ]
                        """, true)).andReturn();
        assertNoSession(currencies);

        var markets = mockMvc.perform(get("/api/v1/reference/markets").header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().json("""
                        [
                          {
                            "id":"10000000-0000-0000-0000-000000000002",
                            "code":"MANUAL",
                            "name":"Manual or unlisted market",
                            "marketType":"MANUAL",
                            "countryCode":null,
                            "timeZone":"UTC",
                            "quotationCurrencies":["EUR","GBP","TRY","USD"],
                            "primaryQuotationCurrency":"TRY",
                            "active":true,
                            "sourceKind":"REFERENCE_SEED"
                          },
                          {
                            "id":"10000000-0000-0000-0000-000000000001",
                            "code":"XIST",
                            "name":"Borsa Istanbul",
                            "marketType":"EXCHANGE",
                            "countryCode":"TR",
                            "timeZone":"Europe/Istanbul",
                            "quotationCurrencies":["TRY"],
                            "primaryQuotationCurrency":"TRY",
                            "active":true,
                            "sourceKind":"REFERENCE_SEED"
                          }
                        ]
                        """, true)).andReturn();
        assertNoSession(markets);
    }

    @Test
    void calendarReturnsNoneAndExactMissingDatesWithoutInferringSessions() throws Exception {
        var identity = authenticated("catalog-calendar-none@example.com");

        var result = mockMvc
                .perform(get("/api/v1/reference/markets/{marketId}/calendar", XIST).param("from", "2026-08-01").param("to", "2026-08-03")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache")).andExpect(content().json("""
                        {
                          "marketId":"10000000-0000-0000-0000-000000000001",
                          "marketCode":"XIST",
                          "timeZone":"Europe/Istanbul",
                          "from":"2026-08-01",
                          "to":"2026-08-03",
                          "coverageStatus":"NONE",
                          "sessions":[],
                          "missingDates":["2026-08-01","2026-08-02","2026-08-03"]
                        }
                        """, true)).andReturn();
        assertNoSession(result);
    }

    @Test
    void calendarReturnsExplicitPartialAndCompleteRows() throws Exception {
        insertCalendar(LocalDate.of(2026, 8, 1), "OPEN", LocalTime.of(9, 30), LocalTime.of(17, 0));
        insertCalendar(LocalDate.of(2026, 8, 3), "CLOSED", null, null);
        var identity = authenticated("catalog-calendar-explicit@example.com");

        mockMvc.perform(get("/api/v1/reference/markets/{marketId}/calendar", XIST).param("from", "2026-08-01").param("to", "2026-08-03")
                .header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.coverageStatus", equalTo("PARTIAL")))
                .andExpect(jsonPath("$.sessions.length()", equalTo(2))).andExpect(jsonPath("$.sessions[0].date", equalTo("2026-08-01")))
                .andExpect(jsonPath("$.sessions[0].sessionStatus", equalTo("OPEN"))).andExpect(jsonPath("$.sessions[0].opensAt", equalTo("09:30:00")))
                .andExpect(jsonPath("$.sessions[0].closesAt", equalTo("17:00:00"))).andExpect(jsonPath("$.sessions[1].sessionStatus", equalTo("CLOSED")))
                .andExpect(jsonPath("$.sessions[1].opensAt").doesNotExist()).andExpect(jsonPath("$.missingDates", equalTo(List.of("2026-08-02"))));

        insertCalendar(LocalDate.of(2026, 8, 2), "CLOSED", null, null);
        mockMvc.perform(get("/api/v1/reference/markets/{marketId}/calendar", XIST).param("from", "2026-08-01").param("to", "2026-08-03")
                .header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.coverageStatus", equalTo("COMPLETE")))
                .andExpect(jsonPath("$.missingDates").isEmpty());
    }

    @Test
    void calendarValidationUnknownMarketAndMissingBearerUseSharedContracts() throws Exception {
        mockMvc.perform(get("/api/v1/reference/countries")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer")).andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));

        var identity = authenticated("catalog-calendar-errors@example.com");
        mockMvc.perform(get("/api/v1/reference/markets/{marketId}/calendar", XIST).param("from", "2026-08-03").param("to", "2026-08-01")
                .header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED"))).andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/v1/reference/markets/{marketId}/calendar", XIST).param("from", "2026-01-01").param("to", "2027-01-02")
                .header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        mockMvc.perform(get("/api/v1/reference/markets/{marketId}/calendar", UUID.randomUUID()).param("from", "2026-08-01").param("to", "2026-08-01")
                .header(HttpHeaders.AUTHORIZATION, identity.bearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("MARKET_NOT_FOUND"))).andExpect(jsonPath("$.traceId").exists());
    }

    private Identity authenticated(String email) {
        var userId = registrationService.register(email, "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "reference-http-test");
        return new Identity(userId, tokenIssuanceService.issue(session.sessionId()).accessToken());
    }

    private void insertCalendar(LocalDate date, String status, LocalTime opensAt, LocalTime closesAt) {
        new TransactionTemplate(transactionManager).executeWithoutResult(statusTransaction -> jdbcTemplate.update("INSERT INTO reference.market_calendar" +
                " (market_id, calendar_date, session_status, opens_at, closes_at, source_kind, created_at)" + " VALUES (?, ?, ?, ?, ?, 'REFERENCE_SEED', ?)",
                XIST, date, status, opensAt, closesAt, T0.atOffset(ZoneOffset.UTC)));
    }

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    private record Identity(UUID userId, String bearer) {

        private Identity {
            bearer = "Bearer " + bearer;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }
    }
}
