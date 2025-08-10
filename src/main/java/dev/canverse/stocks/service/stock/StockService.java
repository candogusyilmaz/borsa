package dev.canverse.stocks.service.stock;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.domain.entity.instrument.QInstrument;
import dev.canverse.stocks.service.stock.model.Stocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final JPAQueryFactory queryFactory;

    public Stocks fetchStocks(String exchange) {
        var stock = QInstrument.instrument;

        var result = queryFactory
                .select(
                        Projections.constructor(Stocks.Symbol.class,
                                stock.id.stringValue(),
                                stock.symbol,
                                stock.name,
                                stock.snapshot.last,
                                stock.snapshot.dailyChange,
                                stock.snapshot.dailyChangePercent,
                                stock.snapshot.updatedAt)
                ).from(stock)
                .leftJoin(stock.snapshot)
                .where(stock.market.code.eq(exchange))
                .orderBy(stock.symbol.asc())
                .fetch();

        return new Stocks(exchange, result);
    }

    public List<SelectItem> fetchLookupStocks(Optional<String> market) {
        var stock = QInstrument.instrument;

        var query = queryFactory.select(Projections.constructor(SelectItem.class, stock.id.stringValue(), stock.symbol.concat(" - ").concat(stock.name))).from(stock);

        market.ifPresent(s -> query.where(stock.market.code.eq(s)));

        return query.fetch();
    }
}
