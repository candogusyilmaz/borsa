package dev.canverse.stocks.service.member;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.entity.QHolding;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.member.model.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HoldingService {
    private final JPAQueryFactory queryFactory;

    public Balance fetchBalance() {
        var holding = QHolding.holding;

        var query = queryFactory.select(
                        Projections.constructor(Balance.Stock.class,
                                holding.stock.symbol,
                                holding.quantity,
                                holding.averagePrice,
                                holding.stock.snapshot.last)
                )
                .from(QHolding.holding)
                .where(QHolding.holding.user.id.eq(AuthenticationProvider.getUser().getId()));

        return new Balance(query.fetch());
    }
}
