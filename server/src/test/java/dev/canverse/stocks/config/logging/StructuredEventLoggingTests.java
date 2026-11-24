package dev.canverse.stocks.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.querydsl.jpa.impl.JPAQueryFactory;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.domain.entity.instrument.Instrument;
import dev.canverse.stocks.domain.entity.instrument.Market;
import dev.canverse.stocks.domain.entity.instrument.MarketCurrency;
import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.domain.entity.portfolio.Position;
import dev.canverse.stocks.integration.forex.ForexRate;
import dev.canverse.stocks.integration.forex.ForexWebClient;
import dev.canverse.stocks.repository.CurrencyRepository;
import dev.canverse.stocks.repository.DashboardRepository;
import dev.canverse.stocks.repository.InstrumentRepository;
import dev.canverse.stocks.repository.MarketCurrencyRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.repository.TradeMapper;
import dev.canverse.stocks.repository.TradeRepository;
import dev.canverse.stocks.service.common.CurrencyService;
import dev.canverse.stocks.service.portfolio.PortfolioAccessValidator;
import dev.canverse.stocks.service.portfolio.PortfolioService;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.TradeRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class StructuredEventLoggingTests {

    private ListAppender<ILoggingEvent> portfolioAppender;
    private ListAppender<ILoggingEvent> tradeAppender;
    private ListAppender<ILoggingEvent> currencyAppender;

    private Logger portfolioLogger;
    private Logger tradeLogger;
    private Logger currencyLogger;

    @BeforeEach
    void setUp() {
        portfolioLogger = (Logger) LoggerFactory.getLogger(PortfolioService.class);
        tradeLogger = (Logger) LoggerFactory.getLogger(TradeService.class);
        currencyLogger = (Logger) LoggerFactory.getLogger(CurrencyService.class);

        portfolioAppender = attachAppender(portfolioLogger);
        tradeAppender = attachAppender(tradeLogger);
        currencyAppender = attachAppender(currencyLogger);

        var user = new User("sensitive-user@example.com", "secret-hash");
        user.setId(42L);
        user.setName("Sensitive Name");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        portfolioLogger.detachAppender(portfolioAppender);
        tradeLogger.detachAppender(tradeAppender);
        currencyLogger.detachAppender(currencyAppender);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        SecurityContextHolder.clearContext();
    }

    @Test
    void portfolioCreatedLogsOnlyAfterTransactionCommitAndNotOnRollback() {
        var portfolioRepo = mock(PortfolioRepository.class);
        var dashboardRepo = mock(DashboardRepository.class);
        var validator = mock(PortfolioAccessValidator.class);
        var queryFactory = mock(JPAQueryFactory.class);

        var dashboard = mock(Dashboard.class);
        when(dashboardRepo.findDefaultByUserId(42L)).thenReturn(dashboard);
        when(portfolioRepo.save(any(Portfolio.class)))
                .thenAnswer(
                        inv -> {
                            Portfolio p = inv.getArgument(0);
                            ReflectionTestUtils.setField(p, "id", 101L);
                            return p;
                        });

        var service = new PortfolioService(portfolioRepo, dashboardRepo, validator, queryFactory);

        // Simulate active Spring transaction that rolls back
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.createPortfolio("My Private Retirement Portfolio");
        assertThat(portfolioAppender.list).isEmpty();

        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThat(portfolioAppender.list).isEmpty();

        // Simulate active Spring transaction that commits
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.createPortfolio("My Private Retirement Portfolio");
        assertThat(portfolioAppender.list).isEmpty();

        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThat(portfolioAppender.list).hasSize(1);
        var event = portfolioAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(toMap(event))
                .containsEntry("event", "portfolio_created")
                .containsEntry("portfolioId", 101L);
        assertThat(event.getFormattedMessage())
                .doesNotContain("My Private Retirement Portfolio")
                .doesNotContain("sensitive-user@example.com");
    }

    @Test
    void tradeCreatedLogsSafeFieldsOnlyAndExcludesPriceQuantityAndNotes() {
        var instrumentRepo = mock(InstrumentRepository.class);
        var positionRepo = mock(PositionRepository.class);
        var tradeRepo = mock(TradeRepository.class);
        var validator = mock(PortfolioAccessValidator.class);
        var marketCurrencyRepo = mock(MarketCurrencyRepository.class);
        var tradeMapper = mock(TradeMapper.class);

        var market = mock(Market.class);
        when(market.getId()).thenReturn(1L);
        var instrument = mock(Instrument.class);
        when(instrument.getMarket()).thenReturn(market);

        var portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(15L);

        var position = new Position(portfolio, instrument, "TRY");

        when(validator.validateAccess(15L)).thenReturn(portfolio);
        when(instrumentRepo.findById(99L)).thenReturn(Optional.of(instrument));
        when(marketCurrencyRepo.findById(any()))
                .thenReturn(Optional.of(mock(MarketCurrency.class)));
        when(positionRepo.findBy(42L, 15L, 99L, "TRY")).thenReturn(Optional.of(position));

        var service =
                new TradeService(
                        instrumentRepo,
                        positionRepo,
                        tradeRepo,
                        validator,
                        marketCurrencyRepo,
                        tradeMapper);

        var req =
                new TradeRequest(
                        99L,
                        "TRY",
                        new BigDecimal("1234.56"),
                        new BigDecimal("987.65"),
                        new BigDecimal("12.34"),
                        Instant.now(),
                        "Private trade note about bonus",
                        List.of("secret-tag"));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.buy(15L, req);
        assertThat(tradeAppender.list).isEmpty();
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThat(tradeAppender.list).hasSize(1);
        var event = tradeAppender.list.getFirst();
        var kv = toMap(event);
        assertThat(kv)
                .containsEntry("event", "trade_created")
                .containsEntry("tradeType", "BUY")
                .containsEntry("portfolioId", 15L)
                .containsEntry("instrumentId", 99L)
                .containsEntry("currencyCode", "TRY")
                .doesNotContainKeys("price", "quantity", "commission", "total", "notes", "tags");
        assertThat(event.getFormattedMessage())
                .doesNotContain("1234.56")
                .doesNotContain("987.65")
                .doesNotContain("Private trade note");
    }

    @Test
    void currencySyncFailureDoesNotLeakExternalErrorMessageAndLogsStructuredEvent()
            throws Exception {
        var forexClient = mock(ForexWebClient.class);
        var currencyRepo = mock(CurrencyRepository.class);
        when(forexClient.getForexRates())
                .thenReturn(
                        new ForexRate(
                                false,
                                new ForexRate.Error(101, "Invalid API key sk_live_secret_12345"),
                                "USD",
                                null,
                                null));

        var service = new CurrencyService(forexClient, currencyRepo);
        var method = CurrencyService.class.getDeclaredMethod("updateCurrencyRates");
        method.setAccessible(true);
        method.invoke(service);

        assertThat(currencyAppender.list).hasSize(1);
        var event = currencyAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        var kv = toMap(event);
        assertThat(kv)
                .containsEntry("event", "scheduled_job_failed")
                .containsEntry("job", "currency_rate_sync")
                .containsEntry("provider", "forex_rate_api")
                .containsEntry("errorCode", 101)
                .containsEntry("outcome", "failed");
        assertThat(event.getFormattedMessage()).doesNotContain("sk_live_secret_12345");
        assertThat(kv.values().toString()).doesNotContain("sk_live_secret_12345");
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
        var map = new LinkedHashMap<String, Object>();
        for (var kv : event.getKeyValuePairs()) {
            map.put(kv.key, kv.value);
        }
        return map;
    }
}
