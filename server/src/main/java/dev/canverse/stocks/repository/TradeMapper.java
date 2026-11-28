package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.FetchTradesQuery;
import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeMapper {
    List<TradeInfo> fetchTrades(Long userId, FetchTradesQuery query);

    List<TradeInfo> fetchActiveTrades(Long userId, Long positionId);
}
