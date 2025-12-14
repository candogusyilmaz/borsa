package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.FetchPositionsQuery;
import dev.canverse.stocks.service.portfolio.model.PositionInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PositionMapper {
    List<PositionInfo> fetchPositions(@Param("userId") Long userId, @Param("query") FetchPositionsQuery query);
}
