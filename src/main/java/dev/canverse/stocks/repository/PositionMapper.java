package dev.canverse.stocks.repository;

import dev.canverse.stocks.service.portfolio.model.PositionInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PositionMapper {
    List<PositionInfo> fetchPositions(Long userId);
}
