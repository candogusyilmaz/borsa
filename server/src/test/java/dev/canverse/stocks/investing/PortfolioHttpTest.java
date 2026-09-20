package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.canverse.stocks.investing.application.InvestingTradeCommandService;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.web.request.TradeCommitRequest;
import dev.canverse.stocks.investing.web.request.TradePreviewRequest;
import dev.canverse.stocks.ledger.application.FinancialAccountLifecycleService;
import dev.canverse.stocks.ledger.application.FinancialAccountOnboardingService;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.web.request.ArchiveAccountRequest;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.request.OpeningStateRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import dev.canverse.stocks.reference.application.ManualInstrumentService;
import dev.canverse.stocks.reference.domain.InstrumentType;
import dev.canverse.stocks.reference.domain.ValuationMethod;
import dev.canverse.stocks.reference.web.request.ManualInstrumentCreateRequest;
import dev.canverse.stocks.testing.DatabaseCleaner;
import dev.canverse.stocks.testing.HttpAssertions;
import dev.canverse.stocks.testing.IdentityTestPropertiesConfiguration;
import dev.canverse.stocks.testing.IntegrationTest;
import dev.canverse.stocks.testing.TestClock;
import dev.canverse.stocks.testing.TestClockConfiguration;
import dev.canverse.stocks.testing.TestIdentitySupport;
import dev.canverse.stocks.testing.TestIdentitySupport.Identity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
@IntegrationTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"stocks.identity.refresh-session.lifetime=2h", "stocks.identity.access-token.key-id=portfolio-test"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Import({IdentityTestPropertiesConfiguration.class, TestClockConfiguration.class})
class PortfolioHttpTest {

    @BeforeEach
    void resetTestClock() {
        testClock.setInstant(OBSERVED_AT);
        databaseCleaner.resetApplicationState();
    }
    @Autowired
    TestClock testClock;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    TestIdentitySupport testIdentitySupport;
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-19T12:00:00Z");
    private static final Instant OPENED_AT = Instant.parse("2026-09-19T10:00:00Z");
    private static final UUID MANUAL_MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    @Autowired
    MockMvc mockMvc;

    @Autowired
    FinancialAccountOnboardingService accountService;

    @Autowired
    FinancialAccountLifecycleService accountLifecycleService;

    @Autowired
    InvestingTradeCommandService tradeService;

    @Autowired
    ManualInstrumentService instrumentService;

    @Test
    void authenticatedOwnerCanCreateListUpdateArchiveAndReusePortfolioNames() throws Exception {
        var owner = testIdentitySupport.create("portfolio-http-owner@example.com");
        var other = testIdentitySupport.create("portfolio-http-other@example.com");
        var zulu = createAccount(owner.userId(), "Zulu brokerage", "HOLDINGS_ONLY", null);
        var alpha = createAccount(owner.userId(), "Alpha brokerage", "HOLDINGS_ONLY", null);

        mockMvc.perform(get("/api/v1/portfolios")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/portfolios").contentType(MediaType.APPLICATION_JSON).content(portfolioJson("Unauthenticated", List.of())))
                .andExpect(status().isUnauthorized());
        var missingPortfolioId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}", missingPortfolioId)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", missingPortfolioId).contentType(MediaType.APPLICATION_JSON)
                .content(updatePortfolioJson("Unauthenticated", List.of(), 0L))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/archive", missingPortfolioId).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", missingPortfolioId.toString())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", missingPortfolioId.toString())).andExpect(status().isUnauthorized());
        var created = mockMvc
                .perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson(" Long Term ", List.of(zulu, alpha))))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.name", equalTo("Long Term"))).andExpect(jsonPath("$.accountCount", equalTo(2)))
                .andExpect(jsonPath("$.version", equalTo(0))).andExpect(jsonPath("$.accounts.length()", equalTo(2)))
                .andExpect(jsonPath("$.accounts[0].id", equalTo(alpha.toString()))).andExpect(jsonPath("$.accounts[0].kind", equalTo("BROKERAGE")))
                .andExpect(jsonPath("$.accounts[0].trackingMode", equalTo("HOLDINGS_ONLY"))).andExpect(jsonPath("$.accounts[0].currency", equalTo("USD")))
                .andExpect(jsonPath("$.accounts[0].archived", equalTo(false))).andReturn();
        var portfolioId = idFrom(created);
        assertPortfolioResponseShape(created);
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/portfolios/" + portfolioId);
        mockMvc.perform(
                post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(portfolioJson("LONG TERM", List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NAME_CONFLICT")));

        mockMvc.perform(get("/api/v1/portfolios").with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$[0].id", equalTo(portfolioId.toString())))
                .andExpect(jsonPath("$[0].archived", equalTo(false))).andExpect(jsonPath("$[0].accountCount", equalTo(2)));

        var updated = mockMvc
                .perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePortfolioJson("Retirement", List.of(zulu), 0L)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.name", equalTo("Retirement"))).andExpect(jsonPath("$.version", equalTo(1)))
                .andExpect(jsonPath("$.accountCount", equalTo(1))).andExpect(jsonPath("$.accounts[0].id", equalTo(zulu.toString()))).andReturn();
        assertPortfolioResponseShape(updated);
        var identicalUpdate = mockMvc
                .perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(updatePortfolioJson("Retirement", List.of(zulu), 1L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version", equalTo(2))).andExpect(jsonPath("$.name", equalTo("Retirement"))).andReturn();
        assertPortfolioResponseShape(identicalUpdate);
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(updatePortfolioJson("Stale", List.of(alpha), 0L))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_VERSION_CONFLICT")));

        var archived = mockMvc
                .perform(post("/api/v1/portfolios/{portfolioId}/archive", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.archived", equalTo(true)))
                .andExpect(jsonPath("$.version", equalTo(3))).andExpect(jsonPath("$.accountCount", equalTo(1))).andReturn();
        assertPortfolioResponseShape(archived);
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/archive", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("PORTFOLIO_ARCHIVED")));

        mockMvc.perform(get("/api/v1/portfolios").with(owner.asBearer())).andExpect(status().isOk()).andExpect(content -> {
            assertThat(JsonPath.<List<?>>read(content.getResponse().getContentAsString(), "$")).isEmpty();
        });
        mockMvc.perform(get("/api/v1/portfolios").param("includeArchived", "true").with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].archived", equalTo(true))).andExpect(jsonPath("$[0].version", equalTo(3)))
                .andExpect(content -> assertThat(JsonPath.<Map<String, Object>>read(content.getResponse().getContentAsString(), "$[0]").keySet())
                        .containsExactlyInAnyOrder("id", "name", "accountCount", "archived", "version", "createdAt", "updatedAt", "archivedAt"));
        var archivedDetail = mockMvc.perform(get("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.archived", equalTo(true)))
                .andExpect(jsonPath("$.accounts.length()", equalTo(1))).andReturn();
        assertPortfolioResponseShape(archivedDetail);

        var reusedName = mockMvc
                .perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson("LONG TERM", List.of(alpha))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name", equalTo("LONG TERM"))).andExpect(jsonPath("$.version", equalTo(0))).andReturn();
        assertThat(idFrom(reusedName)).isNotEqualTo(portfolioId);

        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}", portfolioId).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(other.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(updatePortfolioJson("Foreign update", List.of(), 3L))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/archive", portfolioId).with(other.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}", UUID.randomUUID()).with(owner.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        assertThat(archived.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
        assertThat(updated.getResponse().getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    void validatesPortfolioInputsAndKeepsCrossOwnerMembershipOpaque() throws Exception {
        var owner = testIdentitySupport.create("portfolio-validation-owner@example.com");
        var other = testIdentitySupport.create("portfolio-validation-other@example.com");
        var ownedAccount = createAccount(owner.userId(), "Owned account", "HOLDINGS_ONLY", null);
        var foreignAccount = createAccount(other.userId(), "Foreign account", "HOLDINGS_ONLY", null);

        mockMvc.perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(portfolioJson("Foreign", List.of(foreignAccount)))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(portfolioJson("Duplicate", List.of(ownedAccount, ownedAccount)))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED"))).andExpect(jsonPath("$.params.errors[0].field", equalTo("accountIds")))
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.investing.duplicate_portfolio_account")));
        mockMvc.perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Null member\",\"accountIds\":[null]}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.common.not_null")));
        mockMvc.perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(portfolioJson("   ", List.of())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.params.errors[0].field", equalTo("name")))
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.investing.invalid_portfolio_name")));
        mockMvc.perform(
                post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(portfolioJson("x".repeat(161), List.of())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.params.errors[0].field", equalTo("name")))
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.investing.invalid_portfolio_name")));
        mockMvc.perform(
                post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON).content(portfolioJson("ß".repeat(81), List.of())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.investing.invalid_portfolio_name")));
        mockMvc.perform(post("/api/v1/portfolios").with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Missing accounts\",\"accountIds\":null}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));

        var portfolio = createPortfolio(owner, "Preserved group", List.of(ownedAccount));
        var portfolioId = idFrom(portfolio);
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(updatePortfolioJson("Invalid replacement", List.of(foreignAccount), 0L))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ACCOUNT_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Preserved group"))).andExpect(jsonPath("$.version", equalTo(0)))
                .andExpect(jsonPath("$.accounts.length()", equalTo(1))).andExpect(jsonPath("$.accounts[0].id", equalTo(ownedAccount.toString())));
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Negative version\",\"accountIds\":[],\"version\":-1}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")));
        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Missing version\",\"accountIds\":[]}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.params.errors[0].key", equalTo("error.fields.common.not_null")));

        createPortfolio(owner, "Zeta group", List.of());
        createPortfolio(owner, "Alpha group", List.of());
        var summaries = mockMvc.perform(get("/api/v1/portfolios").with(owner.asBearer())).andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<Map<String, Object>>>read(summaries.getResponse().getContentAsString(), "$")).extracting(summary -> summary.get("name"))
                .containsExactly("Alpha group", "Preserved group", "Zeta group");
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", UUID.randomUUID().toString()).with(owner.asBearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
    }

    @Test
    void portfolioFiltersUseCurrentWholeAccountMembershipAndPreserveSlices() throws Exception {
        var owner = testIdentitySupport.create("portfolio-filter-owner@example.com");
        var alphaResponse = createAccountResponse(owner.userId(), "Alpha brokerage", "FULL_LEDGER", "100");
        var alpha = alphaResponse.id();
        var beta = createAccount(owner.userId(), "Beta brokerage", "FULL_LEDGER", "100");
        var instrument = instrumentService.create(owner.userId(),
                new ManualInstrumentCreateRequest(MANUAL_MARKET_ID, "PORTFOLIO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "Portfolio equity", InstrumentType.EQUITY, "USD", ValuationMethod.NOT_VALUED, List.of()))
                .id();
        commitBuy(owner.userId(), alpha, instrument, Instant.parse("2026-09-19T11:00:00Z"));
        commitBuy(owner.userId(), beta, instrument, Instant.parse("2026-09-19T11:01:00Z"));

        var group = createPortfolio(owner, "Current members", List.of(alpha));
        var portfolioId = idFrom(group);
        var emptyGroup = createPortfolio(owner, "Empty members", List.of());
        var emptyPortfolioId = idFrom(emptyGroup);
        accountLifecycleService.archive(owner.userId(), alpha, new ArchiveAccountRequest(UUID.randomUUID(), alphaResponse.version()));
        createPortfolio(owner, "Shared members", List.of(beta, alpha));

        var trades = mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.items[0].accountId", equalTo(alpha.toString()))).andReturn();
        HttpAssertions.assertSlice(trades);
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(1))).andExpect(jsonPath("$.items[0].accountId", equalTo(alpha.toString())));
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", emptyPortfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", emptyPortfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).param("accountId", beta.toString()).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(
                get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).param("accountId", beta.toString()).with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)));
        var other = testIdentitySupport.create("portfolio-filter-other@example.com");
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).with(other.asBearer())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PORTFOLIO_NOT_FOUND")));

        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content(updatePortfolioJson("Current members", List.of(beta, alpha), 0L))).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCount", equalTo(2))).andExpect(jsonPath("$.accounts[0].id", equalTo(alpha.toString())))
                .andExpect(jsonPath("$.accounts[0].archived", equalTo(true))).andExpect(jsonPath("$.accounts[1].id", equalTo(beta.toString())))
                .andExpect(jsonPath("$.accounts[1].archived", equalTo(false)));
        var allTrades = mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(2))).andReturn();
        assertThat(JsonPath.<List<String>>read(allTrades.getResponse().getContentAsString(), "$.items[*].accountId"))
                .containsExactlyInAnyOrder(alpha.toString(), beta.toString());
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).param("page", "0").param("size", "1")
                .param("sort", "effectiveAt,desc").with(owner.asBearer())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.hasNext", equalTo(true))).andExpect(jsonPath("$.items[0].accountId", equalTo(beta.toString())));
        var bothPositions = mockMvc
                .perform(
                        get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).param("sort", "accountName,asc").with(owner.asBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", equalTo(2)))
                .andExpect(jsonPath("$.items[0].accountId", equalTo(alpha.toString()))).andExpect(jsonPath("$.items[1].accountId", equalTo(beta.toString())))
                .andReturn();
        HttpAssertions.assertSlice(bothPositions);

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/archive", portfolioId).with(owner.asBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.archived", equalTo(true)));
        mockMvc.perform(get("/api/v1/trades").param("portfolioId", portfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(2)));
        mockMvc.perform(get("/api/v1/investing/positions").param("portfolioId", portfolioId.toString()).with(owner.asBearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(2)));
    }

    private UUID createAccount(UUID ownerId, String name, String trackingMode, String openingAmount) {
        return createAccountResponse(ownerId, name, trackingMode, openingAmount).id();
    }

    private FinancialAccountResponse createAccountResponse(UUID ownerId, String name, String trackingMode, String openingAmount) {
        var account = accountService.create(ownerId,
                new CreateFinancialAccountRequest(UUID.randomUUID(), name, AccountKind.BROKERAGE, TrackingMode.valueOf(trackingMode), "USD", "UTC",
                        trackingMode.equals("FULL_LEDGER") ? NegativeBalancePolicy.HARD_FLOOR : null, null,
                        openingAmount == null ? null : new OpeningStateRequest(openingAmount, OPENED_AT)));
        return account;
    }

    private MvcResult createPortfolio(Identity identity, String name, List<UUID> accountIds) throws Exception {
        return mockMvc
                .perform(post("/api/v1/portfolios").with(identity.asBearer()).contentType(MediaType.APPLICATION_JSON).content(portfolioJson(name, accountIds)))
                .andExpect(status().isCreated()).andReturn();
    }

    private void commitBuy(UUID ownerId, UUID accountId, UUID instrumentId, Instant effectiveAt) {
        var preview = tradeService.preview(ownerId,
                new TradePreviewRequest(accountId, instrumentId, TradeSide.BUY, "1", "10", "0", RecordingMode.CURRENT_ACTION, effectiveAt, 0L, false));
        tradeService.commit(ownerId, new TradeCommitRequest(UUID.randomUUID(), accountId, instrumentId, TradeSide.BUY, "1", "10", "0",
                RecordingMode.CURRENT_ACTION, effectiveAt, 0L, false, preview.cashBalanceVersion(), preview.positionVersion()));
    }

    private static String portfolioJson(String name, List<UUID> accountIds) {
        return "{\"name\":\"%s\",\"accountIds\":[%s]}".formatted(name,
                accountIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(",")));
    }

    private static String updatePortfolioJson(String name, List<UUID> accountIds, long version) {
        var create = portfolioJson(name, accountIds);
        return create.substring(0, create.length() - 1) + ",\"version\":" + version + "}";
    }

    private static UUID idFrom(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.id"));
    }

    private static void assertPortfolioResponseShape(MvcResult result) throws Exception {
        var body = JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$");
        assertThat(body.keySet()).containsExactlyInAnyOrder("id", "name", "accountCount", "archived", "version", "createdAt", "updatedAt", "archivedAt",
                "accounts");
        var accounts = JsonPath.<List<Map<String, Object>>>read(result.getResponse().getContentAsString(), "$.accounts");
        assertThat(accounts).isNotEmpty();
        for (var account : accounts) {
            assertThat(account.keySet()).containsExactlyInAnyOrder("id", "name", "kind", "trackingMode", "currency", "archived", "archivedAt");
        }
    }

}
