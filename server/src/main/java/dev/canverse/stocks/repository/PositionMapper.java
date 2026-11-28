package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.FetchPositionsQuery;
import dev.canverse.stocks.service.portfolio.model.PositionInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PositionMapper {
    List<PositionInfo> fetchPositions(@Param("userId") Long userId, @Param("query") FetchPositionsQuery query);
}
