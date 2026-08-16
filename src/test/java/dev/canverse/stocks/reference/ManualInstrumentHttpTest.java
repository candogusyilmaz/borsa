package dev.canverse.stocks.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.identity.application.AccessTokenIssuanceService;
import dev.canverse.stocks.identity.application.LocalAccountRegistrationService;
import dev.canverse.stocks.identity.application.RefreshSessionIssuanceService;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import dev.canverse.stocks.reference.domain.ManualInstrumentConstraints;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "stocks.identity.refresh-session.lifetime=2h",
            "stocks.identity.access-token.issuer=https://issuer.test",
            "stocks.identity.access-token.audience=canverse-test-api",
            "stocks.identity.access-token.lifetime=5m",
            "stocks.identity.access-token.key-id=test-ephemeral"
        })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
@Import(ManualInstrumentHttpTest.TestOverrides.class)
@Execution(ExecutionMode.SAME_THREAD)
class ManualInstrumentHttpTest {

    private static final UUID XIST = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANUAL = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID GLOBAL_ID = UUID.fromString("70000000-0000-4000-8000-000000000007");
    private static final UUID GLOBAL_XIST_ID = UUID.fromString("70000000-0000-4000-8000-000000000008");
    private static final UUID GLOBAL_INACTIVE_ID = UUID.fromString("70000000-0000-4000-8000-000000000009");
    private static final UUID MATRIX_GLOBAL_ID = UUID.fromString("73000000-0000-4000-8000-000000000001");
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
        jdbcTemplate.execute(
                "TRUNCATE TABLE reference.instrument_alias, reference.instrument, reference.market_calendar,"
                        + " platform.security_event, identity.device_session, identity.auth_identity,"
                        + " identity.user_account CASCADE");
    }

    @Test
    void createsReadsAndUpdatesOnlyOwnerManagedFields() throws Exception {
        var identity = authenticated("instrument-owner@example.com");
        var createdResult = mockMvc.perform(post("/api/v1/reference/instruments")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(" my-fund ", " My manually valued fund ", "GBP", "USER", " Pension Fund ")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.ownerId", equalTo(identity.userId().toString())))
                .andExpect(jsonPath("$.marketId", equalTo(MANUAL.toString())))
                .andExpect(jsonPath("$.marketCode", equalTo("MANUAL")))
                .andExpect(jsonPath("$.symbol", equalTo("my-fund")))
                .andExpect(jsonPath("$.name", equalTo("My manually valued fund")))
                .andExpect(jsonPath("$.instrumentType", equalTo("FUND")))
                .andExpect(jsonPath("$.quotationCurrency", equalTo("GBP")))
                .andExpect(jsonPath("$.valuationMethod", equalTo("MANUAL_VALUE")))
                .andExpect(jsonPath("$.active", equalTo(true)))
                .andExpect(jsonPath("$.sourceKind", equalTo("USER_ENTERED")))
                .andExpect(jsonPath("$.version", equalTo(0)))
                .andExpect(jsonPath("$.createdAt", equalTo(T0.toString())))
                .andExpect(jsonPath("$.updatedAt", equalTo(T0.toString())))
                .andExpect(jsonPath("$.aliases.length()", equalTo(1)))
                .andExpect(jsonPath("$.aliases[0].type", equalTo("USER")))
                .andExpect(jsonPath("$.aliases[0].value", equalTo("Pension Fund")))
                .andExpect(jsonPath("$.symbolNormalized").doesNotExist())
                .andExpect(jsonPath("$.nameNormalized").doesNotExist())
                .andReturn();
        assertNoSession(createdResult);
        var instrumentId = idFrom(createdResult);

        mockMvc.perform(get("/api/v1/reference/instruments/{instrumentId}", instrumentId)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.id", equalTo(instrumentId.toString())))
                .andExpect(jsonPath("$.version", equalTo(0)))
                .andExpect(jsonPath("$.aliases[0].value", equalTo("Pension Fund")));

        var update = mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", instrumentId)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0, " Updated fund ", "NOT_VALUED", false, "TICKER", "UPDATED")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.id", equalTo(instrumentId.toString())))
                .andExpect(jsonPath("$.ownerId", equalTo(identity.userId().toString())))
                .andExpect(jsonPath("$.marketId", equalTo(MANUAL.toString())))
                .andExpect(jsonPath("$.symbol", equalTo("my-fund")))
                .andExpect(jsonPath("$.quotationCurrency", equalTo("GBP")))
                .andExpect(jsonPath("$.name", equalTo("Updated fund")))
                .andExpect(jsonPath("$.valuationMethod", equalTo("NOT_VALUED")))
                .andExpect(jsonPath("$.active", equalTo(false)))
                .andExpect(jsonPath("$.version", equalTo(1)))
                .andExpect(jsonPath("$.aliases[0].type", equalTo("TICKER")))
                .andExpect(jsonPath("$.aliases[0].value", equalTo("UPDATED")))
                .andReturn();
        assertNoSession(update);

        mockMvc.perform(get("/api/v1/reference/instruments/{instrumentId}", instrumentId)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)))
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    @Test
    void crossOwnerRowsAreIndistinguishableFromUnknownAndGlobalRowsRemainReadOnly() throws Exception {
        var owner = authenticated("instrument-private-owner@example.com");
        var other = authenticated("instrument-other-owner@example.com");
        var privateResult = create(owner, "PRIVATE", "Private instrument", "GBP", "USER", "private");
        var privateId = idFrom(privateResult);
        insertGlobalInstrument();

        assertProblem(
                mockMvc.perform(get("/api/v1/reference/instruments/{instrumentId}", privateId)
                                .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "INSTRUMENT_NOT_FOUND");
        assertProblem(
                mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", privateId)
                                .header(HttpHeaders.AUTHORIZATION, other.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson(0, "Leaked", "MANUAL_VALUE", true, "USER", "leaked")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "INSTRUMENT_NOT_FOUND");
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "PRIVATE")
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        mockMvc.perform(get("/api/v1/reference/instruments/{instrumentId}", GLOBAL_ID)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.sourceKind", equalTo("REFERENCE_SEED")))
                .andExpect(jsonPath("$.active", equalTo(true)));
        assertProblem(
                mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", GLOBAL_ID)
                                .header(HttpHeaders.AUTHORIZATION, other.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson(0, "No mutation", "MANUAL_VALUE", true, "USER", "no mutation")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "INSTRUMENT_NOT_FOUND");
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "GLOBAL")
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(1)))
                .andExpect(jsonPath("$.instruments[0].ownerManaged", equalTo(false)));
    }

    @Test
    void searchUsesExactPrefixFiltersStableCursorAndInactiveOwnerVisibility() throws Exception {
        var identity = authenticated("instrument-search-owner@example.com");
        create(identity, "ALPHA", "Local alpha", "GBP", "USER", "Local alpha alias");
        var beta = idFrom(create(identity, "BETA", "Local beta", "GBP", "USER", "Local beta alias"));
        create(identity, "GAMMA", "Local gamma", "GBP", "USER", "Local gamma alias");
        mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", beta)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0, "Local beta", "MANUAL_VALUE", false, "USER", "Local beta alias")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "LOCAL")
                        .param("limit", "10")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(2)))
                .andExpect(jsonPath("$.instruments[0].symbol", equalTo("ALPHA")))
                .andExpect(jsonPath("$.instruments[0].aliases[0].value", equalTo("Local alpha alias")))
                .andExpect(jsonPath("$.instruments[1].symbol", equalTo("GAMMA")))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        var firstPage = mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "LOCAL")
                        .param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(1)))
                .andExpect(jsonPath("$.instruments[0].symbol", equalTo("ALPHA")))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();
        var cursor = JsonPath.<String>read(firstPage.getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "LOCAL")
                        .param("limit", "1")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(1)))
                .andExpect(jsonPath("$.instruments[0].symbol", equalTo("GAMMA")))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        assertProblem(
                mockMvc.perform(get("/api/v1/reference/instruments")
                                .param("query", "GAMMA")
                                .param("limit", "1")
                                .param("cursor", cursor)
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "INVALID_INSTRUMENT_CURSOR");

        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "LOCAL")
                        .param("includeInactive", "true")
                        .param("limit", "10")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(3)))
                .andExpect(jsonPath("$.instruments[1].symbol", equalTo("BETA")))
                .andExpect(jsonPath("$.instruments[1].active", equalTo(false)))
                .andExpect(jsonPath("$.instruments[1].ownerManaged", equalTo(true)));
    }

    @Test
    void searchHttpCoversPrefixFilterCollisionLiteralCursorAndOwnershipMatrix() throws Exception {
        var owner = authenticated("instrument-search-matrix-owner@example.com");
        var other = authenticated("instrument-search-matrix-other@example.com");
        insertFixture(MATRIX_GLOBAL_ID, null, MANUAL, "SAME", "Shared global fund", "FUND", true, "COLLIDE");
        insertFixture(GLOBAL_XIST_ID, null, XIST, "SAME", "Shared exchange fund", "ETF", true, "COLLIDE");
        insertFixture(GLOBAL_INACTIVE_ID, null, MANUAL, "HIDDEN-GLOBAL", "Hidden global fund", "FUND", false, "HIDDEN");
        var ownerSame = UUID.fromString("74000000-0000-4000-8000-000000000001");
        var ownerInactive = UUID.randomUUID();
        var percent = UUID.randomUUID();
        var underscore = UUID.randomUUID();
        var backslash = UUID.randomUUID();
        insertFixture(ownerSame, owner.userId(), MANUAL, "SAME", "Shared owner fund", "FUND", true, "collide");
        insertFixture(
                ownerInactive, owner.userId(), MANUAL, "HIDDEN-OWNER", "Hidden owner fund", "FUND", false, "HIDDEN");
        insertFixture(percent, owner.userId(), MANUAL, "PERCENT", "Literal%Name", "FUND", true, "Literal%Alias");
        insertFixture(underscore, owner.userId(), MANUAL, "UNDERSCORE", "Literal_Name", "ETF", true, "Literal_Alias");
        insertFixture(backslash, owner.userId(), MANUAL, "BACKSLASH", "Literal\\Name", "FUND", true, "Literal\\Alias");

        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "SAME")
                        .param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(3)))
                .andExpect(jsonPath("$.instruments[0].marketCode", equalTo("MANUAL")))
                .andExpect(jsonPath("$.instruments[1].marketCode", equalTo("MANUAL")))
                .andExpect(jsonPath("$.instruments[2].marketCode", equalTo("XIST")))
                .andExpect(jsonPath("$.instruments[0].ownerManaged", equalTo(false)))
                .andExpect(jsonPath("$.instruments[1].ownerManaged", equalTo(true)))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "SHARED")
                        .param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(3)));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "COLLIDE")
                        .param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(3)));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("marketId", XIST.toString())
                        .param("type", "ETF")
                        .param("limit", "100")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments.length()", equalTo(1)))
                .andExpect(jsonPath("$.instruments[0].id", equalTo(GLOBAL_XIST_ID.toString())));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "Literal%")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[*].id", equalTo(List.of(percent.toString()))));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "Literal_")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[*].id", equalTo(List.of(underscore.toString()))));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "Literal\\")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[*].id", equalTo(List.of(backslash.toString()))));
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "HIDDEN")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments").isEmpty());
        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "HIDDEN")
                        .param("includeInactive", "true")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[*].id", equalTo(List.of(ownerInactive.toString()))));

        var expected = new ArrayList<UUID>();
        for (var index = 0; index < 37; index++) {
            var id = UUID.fromString("72000000-0000-4000-8000-%012d".formatted(index + 1));
            expected.add(id);
            insertFixture(
                    id,
                    owner.userId(),
                    MANUAL,
                    "HTTP-CURSOR-%03d".formatted(index),
                    "HTTP cursor fund " + index,
                    "FUND",
                    true,
                    "HTTP-CURSOR-ALIAS-%03d".formatted(index));
        }
        var otherCursorId = UUID.fromString("72000000-0000-4000-8000-000000000100");
        insertFixture(
                otherCursorId,
                other.userId(),
                MANUAL,
                "HTTP-CURSOR-999",
                "Other cursor fund",
                "FUND",
                true,
                "OTHER-CURSOR");

        var collected = new ArrayList<UUID>();
        String cursor = null;
        String firstCursor = null;
        do {
            var requestBuilder = get("/api/v1/reference/instruments")
                    .param("query", "HTTP-CURSOR-")
                    .param("limit", "6");
            if (cursor != null) {
                requestBuilder.param("cursor", cursor);
            }
            var request = mockMvc.perform(requestBuilder.header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                    .andExpect(status().isOk())
                    .andReturn();
            var ids = JsonPath.<List<String>>read(request.getResponse().getContentAsString(), "$.instruments[*].id");
            collected.addAll(ids.stream().map(UUID::fromString).toList());
            cursor = JsonPath.read(request.getResponse().getContentAsString(), "$.nextCursor");
            if (firstCursor == null) {
                firstCursor = cursor;
            }
        } while (cursor != null);
        assertThat(collected).containsExactlyElementsOf(expected);
        assertThat(collected).doesNotHaveDuplicates();

        mockMvc.perform(get("/api/v1/reference/instruments")
                        .param("query", "HTTP-CURSOR-")
                        .param("limit", "100")
                        .param("cursor", firstCursor)
                        .header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[*].id", equalTo(List.of(otherCursorId.toString()))));
    }

    @Test
    void conflictsInactiveReferencesUnsupportedCurrencyAndMalformedRequestsAreSafe() throws Exception {
        var identity = authenticated("instrument-errors@example.com");
        create(identity, "DUPLICATE", "Duplicate", "GBP", "USER", "duplicate");

        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("duplicate", "Duplicate again", "GBP", "USER", "again")))
                        .andExpect(status().isConflict())
                        .andReturn(),
                "DUPLICATE_INSTRUMENT");
        create(identity, "ALIAS-DUP", "Alias duplicate", "GBP", "USER", "Same");
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "marketId":"10000000-0000-0000-0000-000000000002",
                                  "symbol":"ALIAS-DUP-2",
                                  "name":"Alias duplicate two",
                                  "instrumentType":"FUND",
                                  "quotationCurrency":"GBP",
                                  "valuationMethod":"MANUAL_VALUE",
                                  "aliases":[
                                    {"type":"USER","value":"same"},
                                    {"type":"USER","value":"SAME"}
                                  ]
                                }
                                """))
                        .andExpect(status().isConflict())
                        .andReturn(),
                "DUPLICATE_INSTRUMENT_ALIAS");

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        jdbcTemplate.update("UPDATE reference.market SET active = false WHERE id = ?", MANUAL));
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("INACTIVE", "Inactive market", "GBP", "USER", "inactive")))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "INACTIVE_REFERENCE");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        jdbcTemplate.update("UPDATE reference.market SET active = true WHERE id = ?", MANUAL));

        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJsonForMarket(XIST, "UNSUPPORTED", "Unsupported", "USD")))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "UNSUPPORTED_MARKET_CURRENCY");

        var versioned = idFrom(create(identity, "VERSIONED", "Versioned", "GBP", "USER", "versioned"));
        mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", versioned)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0, "Changed", "NOT_VALUED", true, "USER", "changed")))
                .andExpect(status().isOk());
        assertProblem(
                mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", versioned)
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson(0, "Stale", "MANUAL_VALUE", true, "USER", "stale")))
                        .andExpect(status().isConflict())
                        .andReturn(),
                "INSTRUMENT_VERSION_CONFLICT");

        assertProblem(
                mockMvc.perform(get("/api/v1/reference/instruments")
                                .param("cursor", "not-a-canonical-cursor")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "INVALID_INSTRUMENT_CURSOR");
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "VALIDATION_FAILED");
        assertProblem(
                mockMvc.perform(get("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                        .andExpect(status().isUnauthorized())
                        .andReturn(),
                "INVALID_CREDENTIALS");
    }

    @Test
    void unknownAndInactiveReferenceFailuresReturnExactErrorsWithoutWrites() throws Exception {
        var identity = authenticated("instrument-reference-errors-http@example.com");

        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJsonForMarket(
                                        UUID.randomUUID(), "UNKNOWN-MARKET", "Unknown market", "GBP")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "MARKET_NOT_FOUND");
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("UNKNOWN-CURRENCY", "Unknown currency", "ZZZ", "USER", "alias")))
                        .andExpect(status().isNotFound())
                        .andReturn(),
                "CURRENCY_NOT_FOUND");

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        jdbcTemplate.update("UPDATE reference.currency SET active = false WHERE code = 'GBP'"));
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("INACTIVE-CURRENCY", "Inactive currency", "GBP", "USER", "alias")))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "INACTIVE_REFERENCE");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        jdbcTemplate.update("UPDATE reference.currency SET active = true WHERE code = 'GBP'"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        identity.userId()))
                .isZero();
    }

    @Test
    void malformedEnumCodesUseTheSharedMalformedRequestContract() throws Exception {
        var identity = authenticated("instrument-enum-errors-http@example.com");
        var valid = createJson("ENUM-CODE", "Enum code", "GBP", "USER", "alias");
        var invalidBodies = List.of(
                valid.replace("\"instrumentType\":\"FUND\"", "\"instrumentType\":\"fund\""),
                valid.replace("\"valuationMethod\":\"MANUAL_VALUE\"", "\"valuationMethod\":\"UNKNOWN\""),
                createJson("ENUM-ALIAS", "Enum alias", "GBP", "UNKNOWN", "alias"));

        for (var body : invalidBodies) {
            assertProblem(
                    mockMvc.perform(post("/api/v1/reference/instruments")
                                    .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status().isBadRequest())
                            .andReturn(),
                    "MALFORMED_REQUEST");
        }
        assertProblem(
                mockMvc.perform(get("/api/v1/reference/instruments")
                                .param("type", "fund")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                        .andExpect(status().isBadRequest())
                        .andReturn(),
                "MALFORMED_REQUEST");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        identity.userId()))
                .isZero();
    }

    @Test
    void normalizationExpansionIsAValidationFailureRatherThanADatabaseError() throws Exception {
        var identity = authenticated("instrument-normalization-http@example.com");

        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("EXPANDING-NAME", "ß".repeat(81), "GBP", "USER", "safe alias")))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "VALIDATION_FAILED");
        assertProblem(
                mockMvc.perform(post("/api/v1/reference/instruments")
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson("EXPANDING-ALIAS", "Safe name", "GBP", "USER", "ß".repeat(65))))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "VALIDATION_FAILED");

        var stable = idFrom(create(identity, "EXPANSION-UPDATE", "Stable name", "GBP", "USER", "stable"));
        assertProblem(
                mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", stable)
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson(0, "ß".repeat(81), "MANUAL_VALUE", true, "USER", "stable")))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "VALIDATION_FAILED");
        assertProblem(
                mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", stable)
                                .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson(0, "Stable name", "MANUAL_VALUE", true, "USER", "ß".repeat(65))))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn(),
                "VALIDATION_FAILED");
        mockMvc.perform(get("/api/v1/reference/instruments/{instrumentId}", stable)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(0)))
                .andExpect(jsonPath("$.name", equalTo("Stable name")));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reference.instrument WHERE owner_user_account_id = ?",
                        Integer.class,
                        identity.userId()))
                .isEqualTo(1);
    }

    @Test
    void paddedMaximumValuesAreAcceptedAfterTrimOnCreateAndUpdate() throws Exception {
        var identity = authenticated("instrument-padded-boundary-http@example.com");
        var symbol = " " + "S".repeat(ManualInstrumentConstraints.MAX_SYMBOL_LENGTH) + " ";
        var name = " " + "N".repeat(ManualInstrumentConstraints.MAX_NAME_LENGTH) + " ";
        var alias = " " + "A".repeat(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH) + " ";

        var created = mockMvc.perform(post("/api/v1/reference/instruments")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(symbol, name, "GBP", "USER", alias)))
                .andExpect(status().isCreated())
                .andReturn();
        var instrumentId = idFrom(created);
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(), "$.symbol"))
                .hasSize(ManualInstrumentConstraints.MAX_SYMBOL_LENGTH);
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(), "$.name"))
                .hasSize(ManualInstrumentConstraints.MAX_NAME_LENGTH);
        assertThat(JsonPath.<String>read(created.getResponse().getContentAsString(), "$.aliases[0].value"))
                .hasSize(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH);

        var updated = mockMvc.perform(put("/api/v1/reference/instruments/{instrumentId}", instrumentId)
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(0, name, "NOT_VALUED", true, "USER", alias)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", equalTo(1)))
                .andReturn();
        assertThat(JsonPath.<String>read(updated.getResponse().getContentAsString(), "$.name"))
                .hasSize(ManualInstrumentConstraints.MAX_NAME_LENGTH);
        assertThat(JsonPath.<String>read(updated.getResponse().getContentAsString(), "$.aliases[0].value"))
                .hasSize(ManualInstrumentConstraints.MAX_ALIAS_VALUE_LENGTH);
    }

    private MvcResult create(
            Identity identity, String symbol, String name, String currency, String aliasType, String alias)
            throws Exception {
        return mockMvc.perform(post("/api/v1/reference/instruments")
                        .header(HttpHeaders.AUTHORIZATION, identity.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(symbol, name, currency, aliasType, alias)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private Identity authenticated(String email) {
        var userId = registrationService.register(email, "correct horse battery staple");
        var session = sessionIssuanceService.issue(userId, "instrument-http-test");
        return new Identity(
                userId, tokenIssuanceService.issue(session.sessionId()).accessToken());
    }

    private void insertGlobalInstrument() {
        insertFixture(GLOBAL_ID, null, MANUAL, "GLOBAL", "Global instrument", "ETF", true, "GLOBAL");
    }

    private void insertFixture(
            UUID id,
            UUID ownerId,
            UUID marketId,
            String symbol,
            String name,
            String instrumentType,
            boolean active,
            String alias) {
        var normalizedSymbol = symbol.toUpperCase(java.util.Locale.ROOT);
        var normalizedName = name.toUpperCase(java.util.Locale.ROOT);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "INSERT INTO reference.instrument"
                            + " (id, owner_user_account_id, market_id, symbol, symbol_normalized, name, name_normalized,"
                            + " instrument_type, quotation_currency_code, valuation_method, active, source_kind, version, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'TRY', 'MANUAL_VALUE', ?, ?, 0, ?, ?)",
                    id,
                    ownerId,
                    marketId,
                    symbol,
                    normalizedSymbol,
                    name,
                    normalizedName,
                    instrumentType,
                    active,
                    ownerId == null ? "REFERENCE_SEED" : "USER_ENTERED",
                    T0.atOffset(ZoneOffset.UTC),
                    T0.atOffset(ZoneOffset.UTC));
            jdbcTemplate.update(
                    "INSERT INTO reference.instrument_alias"
                            + " (id, instrument_id, alias_type, alias_value, alias_normalized, created_at)"
                            + " VALUES (?, ?, 'USER', ?, ?, ?)",
                    UUID.randomUUID(),
                    id,
                    alias,
                    alias.toUpperCase(java.util.Locale.ROOT),
                    T0.atOffset(ZoneOffset.UTC));
        });
    }

    private static String createJson(String symbol, String name, String currency, String aliasType, String alias) {
        return createJsonForMarket(MANUAL, symbol, name, currency, aliasType, alias);
    }

    private static String createJsonForMarket(UUID marketId, String symbol, String name, String currency) {
        return createJsonForMarket(marketId, symbol, name, currency, "USER", "alias");
    }

    private static String createJsonForMarket(
            UUID marketId, String symbol, String name, String currency, String aliasType, String alias) {
        return """
                {
                  "marketId":"%s",
                  "symbol":"%s",
                  "name":"%s",
                  "instrumentType":"FUND",
                  "quotationCurrency":"%s",
                  "valuationMethod":"MANUAL_VALUE",
                  "aliases":[{"type":"%s","value":"%s"}]
                }
                """.formatted(marketId, symbol, name, currency, aliasType, alias);
    }

    private static String updateJson(
            long version, String name, String valuationMethod, boolean active, String aliasType, String alias) {
        return """
                {
                  "version":%d,
                  "name":"%s",
                  "valuationMethod":"%s",
                  "active":%s,
                  "aliases":[{"type":"%s","value":"%s"}]
                }
                """.formatted(version, name, valuationMethod, active, aliasType, alias);
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static void assertProblem(MvcResult result, String code) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        if (code != null) {
            assertThat(body.get("code")).isEqualTo(code);
        }
        assertThat(body).containsKeys("type", "title", "status", "instance", "code", "key", "traceId", "timestamp");
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isNotBlank();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER))
                .isNotBlank();
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
