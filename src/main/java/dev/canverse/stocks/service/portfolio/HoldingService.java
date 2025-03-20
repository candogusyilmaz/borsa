package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.HoldingDailySnapshotRepository;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {
    private final HoldingRepository holdingRepository;
    private final HoldingDailySnapshotRepository holdingDailySnapshotRepository;

    @Transactional
    public void deleteAllHoldings() {
        holdingRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }

    public Portfolio fetchPortfolio(boolean includeCommission) {
        return holdingRepository.fetchPortfolio(includeCommission, AuthenticationProvider.getUser().getId());
    }

    public List<BalanceHistory> fetchBalanceHistory(int lastDays) {
        return holdingRepository.fetchBalanceHistory(lastDays, AuthenticationProvider.getUser().getId());
    }

    public void generateDailyHoldingSnapshots() {
        log.info("Generating daily holding snapshots");
        holdingDailySnapshotRepository.generateDailyHoldingSnapshots();
        log.info("Finished generating daily holding snapshots");
    }
}
