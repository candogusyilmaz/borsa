package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.investing.application.PortfolioService;
import dev.canverse.stocks.investing.domain.Portfolio;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.PortfolioAccountMembershipRepository;
import dev.canverse.stocks.investing.infrastructure.PortfolioReadRepository;
import dev.canverse.stocks.investing.infrastructure.PortfolioRepository;
import dev.canverse.stocks.investing.web.request.CreatePortfolioRequest;
import dev.canverse.stocks.investing.web.request.UpdatePortfolioRequest;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import dev.canverse.stocks.ledger.application.LedgerAccountAccess;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PortfolioServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-19T12:00:00Z");

    @Test
    void createChecksOwnedAccountsThenPersistsAndFlushesAggregateAndMembers() {
        var ownerId = UUID.randomUUID();
        var portfolioId = UUID.randomUUID();
        var membershipId = UUID.randomUUID();
        var firstAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var secondAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var entityManager = mock(EntityManager.class);
        var portfolioRepository = mock(PortfolioRepository.class);
        var membershipRepository = mock(PortfolioAccountMembershipRepository.class);
        var readRepository = mock(PortfolioReadRepository.class);
        var ledgerAccountAccess = mock(LedgerAccountAccess.class);
        var idGenerator = mock(IdGenerator.class);
        var portfolioResponse = new PortfolioResponse(portfolioId, "Savings", 0, false, 0L, OBSERVED_AT, OBSERVED_AT, null, List.of());
        var service = new PortfolioService(entityManager, portfolioRepository, membershipRepository, readRepository, ledgerAccountAccess,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC), idGenerator);
        when(idGenerator.next()).thenReturn(portfolioId, membershipId, UUID.randomUUID());
        when(portfolioRepository.existsActiveName(ownerId, "SAVINGS", null)).thenReturn(false);
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(readRepository.findDetail(ownerId, portfolioId)).thenReturn(Optional.of(portfolioResponse));

        var response = service.create(ownerId, new CreatePortfolioRequest(" Savings ", List.of(secondAccountId, firstAccountId)));

        assertThat(response).isSameAs(portfolioResponse);
        InOrder order = inOrder(ledgerAccountAccess, portfolioRepository, entityManager, membershipRepository, readRepository);
        order.verify(ledgerAccountAccess).requireOwnedAccounts(ownerId, List.of(firstAccountId, secondAccountId));
        order.verify(portfolioRepository).existsActiveName(ownerId, "SAVINGS", null);
        order.verify(portfolioRepository).save(any(Portfolio.class));
        order.verify(entityManager).flush();
        order.verify(membershipRepository).saveAll(anyList());
        order.verify(entityManager).flush();
        order.verify(readRepository).findDetail(ownerId, portfolioId);
    }

    @Test
    void invalidMemberSetLeavesPortfolioAndExistingMembershipUntouched() {
        var ownerId = UUID.randomUUID();
        var portfolioId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var portfolio = Portfolio.create(portfolioId, ownerId, "Original", OBSERVED_AT);
        var entityManager = mock(EntityManager.class);
        var portfolioRepository = mock(PortfolioRepository.class);
        var membershipRepository = mock(PortfolioAccountMembershipRepository.class);
        var readRepository = mock(PortfolioReadRepository.class);
        var ledgerAccountAccess = mock(LedgerAccountAccess.class);
        var service = new PortfolioService(entityManager, portfolioRepository, membershipRepository, readRepository, ledgerAccountAccess,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC), UUID::randomUUID);
        when(portfolioRepository.findOwnedForUpdate(ownerId, portfolioId)).thenReturn(Optional.of(portfolio));
        doThrow(new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND)).when(ledgerAccountAccess).requireOwnedAccounts(ownerId, List.of(accountId));

        assertThatThrownBy(() -> service.update(ownerId, portfolioId, new UpdatePortfolioRequest("Changed", List.of(accountId), 0L)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(LedgerErrorCode.ACCOUNT_NOT_FOUND));

        assertThat(portfolio.getName()).isEqualTo("Original");
        assertThat(portfolio.getVersion()).isZero();
        verify(membershipRepository, never()).deleteOwned(ownerId, portfolioId);
        verify(membershipRepository, never()).saveAll(anyList());
        verify(entityManager, never()).flush();
    }

    @Test
    void staleVersionAndArchivedPortfolioUseDistinctStableConflicts() {
        var ownerId = UUID.randomUUID();
        var portfolioId = UUID.randomUUID();
        var portfolio = Portfolio.create(portfolioId, ownerId, "Archived", OBSERVED_AT);
        portfolio.archive(OBSERVED_AT);
        var entityManager = mock(EntityManager.class);
        var portfolioRepository = mock(PortfolioRepository.class);
        var membershipRepository = mock(PortfolioAccountMembershipRepository.class);
        var readRepository = mock(PortfolioReadRepository.class);
        var ledgerAccountAccess = mock(LedgerAccountAccess.class);
        var service = new PortfolioService(entityManager, portfolioRepository, membershipRepository, readRepository, ledgerAccountAccess,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC), UUID::randomUUID);
        when(portfolioRepository.findOwnedForUpdate(ownerId, portfolioId)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> service.update(ownerId, portfolioId, new UpdatePortfolioRequest("Changed", List.of(), 0L)))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(InvestingErrorCode.PORTFOLIO_ARCHIVED));
        assertThatThrownBy(() -> service.update(ownerId, portfolioId, new UpdatePortfolioRequest("Changed", List.of(), 1L))).isInstanceOfSatisfying(
                AppException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(InvestingErrorCode.PORTFOLIO_VERSION_CONFLICT));
        verify(membershipRepository, never()).deleteOwned(ownerId, portfolioId);
    }
}
