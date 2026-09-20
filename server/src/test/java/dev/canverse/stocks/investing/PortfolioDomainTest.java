package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.canverse.stocks.investing.domain.Portfolio;
import dev.canverse.stocks.investing.web.response.PortfolioAccountResponse;
import dev.canverse.stocks.investing.web.response.PortfolioResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioDomainTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-19T12:00:00Z");

    @Test
    void trimsAndUppercasesNamesAtDisplayAndNormalizedLengthBoundaries() {
        assertThat(Portfolio.displayName("  Long Term  ")).isEqualTo("Long Term");
        assertThat(Portfolio.normalizedName("  Long Term  ")).isEqualTo("LONG TERM");
        assertThat(Portfolio.displayName("x".repeat(160))).hasSize(160);
        assertThat(Portfolio.normalizedName("x".repeat(160))).hasSize(160);
        assertThatIllegalArgumentException().isThrownBy(() -> Portfolio.displayName("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> Portfolio.displayName("x".repeat(161)));
        assertThatIllegalArgumentException().isThrownBy(() -> Portfolio.normalizedName("ß".repeat(81)));
    }

    @Test
    void createRenameAndArchiveAreIntentNamedAndArchiveIsOneWay() {
        var portfolio = Portfolio.create(UUID.randomUUID(), UUID.randomUUID(), "  Long Term  ", OBSERVED_AT);

        assertThat(portfolio.getName()).isEqualTo("Long Term");
        assertThat(portfolio.getNameNormalized()).isEqualTo("LONG TERM");
        assertThat(portfolio.getVersion()).isZero();
        assertThat(portfolio.getCreatedAt()).isEqualTo(OBSERVED_AT);
        assertThat(portfolio.getUpdatedAt()).isEqualTo(OBSERVED_AT);

        portfolio.rename("Retirement", OBSERVED_AT);
        assertThat(portfolio.getName()).isEqualTo("Retirement");
        assertThat(portfolio.getNameNormalized()).isEqualTo("RETIREMENT");
        assertThat(portfolio.getUpdatedAt()).isAfter(OBSERVED_AT);

        portfolio.archive(OBSERVED_AT);
        assertThat(portfolio.isArchived()).isTrue();
        assertThat(portfolio.getArchivedAt()).isEqualTo(OBSERVED_AT);
        assertThatIllegalStateException().isThrownBy(() -> portfolio.rename("Changed", OBSERVED_AT));
        assertThatIllegalStateException().isThrownBy(() -> portfolio.archive(OBSERVED_AT));
    }

    @Test
    void membershipIdsAreOrderInsensitiveAndRejectDuplicates() {
        var first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(Portfolio.normalizeAccountIds(List.of(second, first))).containsExactly(first, second);
        assertThatIllegalArgumentException().isThrownBy(() -> Portfolio.normalizeAccountIds(List.of(first, first)));
    }

    @Test
    void portfolioResponseCopiesItsMemberCollectionAndChecksItsCount() {
        var account = new PortfolioAccountResponse(UUID.randomUUID(), "Brokerage", dev.canverse.stocks.ledger.domain.AccountKind.BROKERAGE,
                dev.canverse.stocks.ledger.domain.TrackingMode.HOLDINGS_ONLY, "USD", false, null);
        var mutableAccounts = new ArrayList<>(List.of(account));
        var response = new PortfolioResponse(UUID.randomUUID(), "Investments", 1, false, 0L, OBSERVED_AT, OBSERVED_AT, null, mutableAccounts);
        mutableAccounts.clear();

        assertThat(response.accounts()).containsExactly(account);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PortfolioResponse(UUID.randomUUID(), "Investments", 0, false, 0L, OBSERVED_AT, OBSERVED_AT, null, List.of(account)));
    }
}
