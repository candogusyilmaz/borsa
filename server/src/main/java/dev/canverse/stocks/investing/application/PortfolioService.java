package dev.canverse.stocks.investing.application;

import dev.canverse.stocks.investing.domain.Portfolio;
import dev.canverse.stocks.investing.domain.PortfolioAccountMembership;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.investing.infrastructure.PortfolioAccountMembershipRepository;
import dev.canverse.stocks.investing.infrastructure.PortfolioReadRepository;
import dev.canverse.stocks.investing.infrastructure.PortfolioRepository;
import dev.canverse.stocks.investing.web.request.ArchivePortfolioRequest;
import dev.canverse.stocks.investing.web.request.CreatePortfolioRequest;
import dev.canverse.stocks.investing.web.request.UpdatePortfolioRequest;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import dev.canverse.stocks.investing.web.response.PortfolioSummaryResponse;
import dev.canverse.stocks.ledger.application.LedgerAccountAccess;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final String INVALID_NAME_KEY = "error.fields.investing.invalid_portfolio_name";
    private static final String DUPLICATE_ACCOUNT_KEY = "error.fields.investing.duplicate_portfolio_account";

    private final EntityManager entityManager;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAccountMembershipRepository membershipRepository;
    private final PortfolioReadRepository readRepository;
    private final LedgerAccountAccess ledgerAccountAccess;
    private final Clock clock;
    private final IdGenerator idGenerator;

    @Transactional
    public PortfolioResponse create(UUID ownerUserAccountId, CreatePortfolioRequest request) {
        var name = validateName(request.name());
        var accountIds = normalizeAccountIds(request.accountIds());
        ledgerAccountAccess.requireOwnedAccounts(ownerUserAccountId, accountIds);

        var normalizedName = Portfolio.normalizedName(name);
        if (portfolioRepository.existsActiveName(ownerUserAccountId, normalizedName, null)) {
            throw new AppException(InvestingErrorCode.PORTFOLIO_NAME_CONFLICT);
        }

        var now = now();
        var portfolio = portfolioRepository.save(Portfolio.create(idGenerator.next(), ownerUserAccountId, name, now));
        entityManager.flush();
        replaceMemberships(ownerUserAccountId, portfolio.getId(), accountIds, now);
        entityManager.flush();
        return detail(ownerUserAccountId, portfolio.getId());
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> list(UUID ownerUserAccountId, boolean includeArchived) {
        return readRepository.findSummaries(ownerUserAccountId, includeArchived);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(UUID ownerUserAccountId, UUID portfolioId) {
        return detail(ownerUserAccountId, portfolioId);
    }

    @Transactional
    public PortfolioResponse update(UUID ownerUserAccountId, UUID portfolioId, UpdatePortfolioRequest request) {
        var portfolio = ownedForUpdate(ownerUserAccountId, portfolioId);
        requireCurrentVersion(portfolio, request.version());
        requireActive(portfolio);

        var name = validateName(request.name());
        var accountIds = normalizeAccountIds(request.accountIds());
        ledgerAccountAccess.requireOwnedAccounts(ownerUserAccountId, accountIds);
        var normalizedName = Portfolio.normalizedName(name);
        if (portfolioRepository.existsActiveName(ownerUserAccountId, normalizedName, portfolioId)) {
            throw new AppException(InvestingErrorCode.PORTFOLIO_NAME_CONFLICT);
        }

        var now = now();
        portfolio.rename(name, now);
        replaceMemberships(ownerUserAccountId, portfolioId, accountIds, now);
        entityManager.flush();
        return detail(ownerUserAccountId, portfolioId);
    }

    @Transactional
    public PortfolioResponse archive(UUID ownerUserAccountId, UUID portfolioId, ArchivePortfolioRequest request) {
        var portfolio = ownedForUpdate(ownerUserAccountId, portfolioId);
        requireCurrentVersion(portfolio, request.version());
        requireActive(portfolio);
        portfolio.archive(now());
        entityManager.flush();
        return detail(ownerUserAccountId, portfolioId);
    }

    private Portfolio ownedForUpdate(UUID ownerUserAccountId, UUID portfolioId) {
        return portfolioRepository.findOwnedForUpdate(ownerUserAccountId, portfolioId)
                .orElseThrow(() -> new AppException(InvestingErrorCode.PORTFOLIO_NOT_FOUND));
    }

    private PortfolioResponse detail(UUID ownerUserAccountId, UUID portfolioId) {
        return readRepository.findDetail(ownerUserAccountId, portfolioId).orElseThrow(() -> new AppException(InvestingErrorCode.PORTFOLIO_NOT_FOUND));
    }

    private static void requireCurrentVersion(Portfolio portfolio, Long version) {
        if (version == null || portfolio.getVersion() != version) {
            throw new AppException(InvestingErrorCode.PORTFOLIO_VERSION_CONFLICT);
        }
    }

    private static void requireActive(Portfolio portfolio) {
        if (portfolio.isArchived()) {
            throw new AppException(InvestingErrorCode.PORTFOLIO_ARCHIVED);
        }
    }

    private void replaceMemberships(UUID ownerUserAccountId, UUID portfolioId, List<UUID> accountIds, Instant createdAt) {
        membershipRepository.deleteOwned(ownerUserAccountId, portfolioId);
        var memberships = accountIds.stream()
                .map(accountId -> PortfolioAccountMembership.create(idGenerator.next(), ownerUserAccountId, portfolioId, accountId, createdAt)).toList();
        membershipRepository.saveAll(memberships);
    }

    private static String validateName(String name) {
        try {
            var displayName = Portfolio.displayName(name);
            Portfolio.normalizedName(displayName);
            return displayName;
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField("name", INVALID_NAME_KEY,
                    "The name must contain 1 to 160 characters in its trimmed display and normalized forms.");
        }
    }

    private static List<UUID> normalizeAccountIds(List<UUID> accountIds) {
        try {
            return Portfolio.normalizeAccountIds(accountIds);
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField("accountIds", DUPLICATE_ACCOUNT_KEY, "Account IDs must not contain duplicates.");
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
