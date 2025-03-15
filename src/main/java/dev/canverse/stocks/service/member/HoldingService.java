package dev.canverse.stocks.service.member;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.domain.entity.QHoldingHistory;
import dev.canverse.stocks.repository.HoldingDailySnapshotRepository;
import dev.canverse.stocks.repository.HoldingRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.member.model.BalanceHistory;
import dev.canverse.stocks.service.member.model.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {
    private final JPAQueryFactory queryFactory;
    private final HoldingRepository holdingRepository;
    private final HoldingDailySnapshotRepository holdingDailySnapshotRepository;

    @Transactional
    public void deleteAllHoldings() {
        holdingRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }

    public Portfolio fetchPortfolio(boolean includeCommission) {
        var holding = QHolding.holding;

        var averagePrice = (includeCommission ?
                holding.total.add(holding.totalTax).divide(holding.quantity) :
                holding.total.divide(holding.quantity)).castToNum(BigDecimal.class);

        var result = queryFactory.select(
                        Projections.constructor(Portfolio.Stock.class,
                                holding.stock.id.stringValue(),
                                holding.stock.symbol,
                                holding.stock.snapshot.dailyChange,
                                holding.stock.snapshot.previousClose,
                                holding.stock.snapshot.dailyChangePercent,
                                holding.quantity,
                                holding.total,
                                holding.totalTax,
                                holding.stock.snapshot.last,
                                averagePrice,
                                holding.stock.snapshot.dailyChange.multiply(holding.quantity),
                                holding.stock.snapshot.last.multiply(holding.quantity)
                        )
                )
                .from(holding)
                .where(holding.user.id.eq(AuthenticationProvider.getUser().getId()).and(holding.quantity.gt(0)))
                .orderBy(holding.stock.symbol.asc())
                .fetch();

        return new Portfolio(result);
    }

    public List<BalanceHistory> fetchBalanceHistory(int lastDays) {
        var holdingHistory = QHoldingHistory.holdingHistory;

        var startDate = LocalDate.now().minusDays(lastDays);
        var startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

        return queryFactory
                .select(
                        Projections.constructor(
                                BalanceHistory.class,
                                holdingHistory.createdAt,
                                holdingHistory.holding.stock.symbol,
                                holdingHistory.total
                        )
                )
                .from(holdingHistory)
                .where(holdingHistory.createdAt.after(startInstant).and(holdingHistory.holding.user.id.eq(AuthenticationProvider.getUser().getId())))
                .groupBy(holdingHistory.createdAt, holdingHistory.holding.stock.symbol)
                .orderBy(holdingHistory.createdAt.desc())
                .fetch();
    }

    public void generateDailyHoldingSnapshots() {
        log.info("Generating daily holding snapshots");
        holdingDailySnapshotRepository.generateDailyHoldingSnapshots();
        log.info("Finished generating daily holding snapshots");
    }
}
