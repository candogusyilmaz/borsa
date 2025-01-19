package dev.canverse.stocks.service.stock;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.domain.entity.QStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockService {
    private final JPAQueryFactory queryFactory;

    public List<SelectItem> fetchLookupStocks(Optional<String> exchange) {
        var stock = QStock.stock;

        var query = queryFactory
                .select(Projections.constructor(SelectItem.class, stock.id.stringValue(), stock.symbol.concat(" - ").concat(stock.name)))
                .from(stock);

        exchange.ifPresent(s -> query.where(stock.exchange.code.eq(s)));

        return query.fetch();
    }
}
