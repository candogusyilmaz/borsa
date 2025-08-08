package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.PositionDailySnapshotRepository;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.BalanceHistory;
import dev.canverse.stocks.service.portfolio.model.PortfolioInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final PositionDailySnapshotRepository positionDailySnapshotRepository;

    @Transactional
    public void deleteAllPositions() {
        positionRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }

    public PortfolioInfo fetchPortfolio(long portfolioId) {
        if (!portfolioRepository.isPortfolioOwnedByPrincipal(portfolioId)) {
            throw new NotFoundException("Portfolio not found");
        }

        return positionRepository.fetchPortfolio(portfolioId);
    }

    public List<BalanceHistory> fetchBalanceHistory(int lastDays) {
        return positionRepository.fetchBalanceHistory(lastDays, AuthenticationProvider.getUser().getId());
    }

    public void generateDailyPositionSnapshots() {
        log.info("Generating daily holding snapshots");
        positionDailySnapshotRepository.generateDailyHoldingSnapshots();
        log.info("Finished generating daily holding snapshots");
    }
}
