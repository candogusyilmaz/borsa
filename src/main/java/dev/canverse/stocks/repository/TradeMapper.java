package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeMapper {
    List<TradeInfo> fetchTrades(Long userId);

    List<TradeInfo> fetchActiveTrades(Long userId, Long positionId);
}
