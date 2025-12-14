package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.PositionMapper;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.model.FetchPositionsQuery;
import dev.canverse.stocks.service.portfolio.model.PositionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {
    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;

    @Transactional
    public void deleteAllPositions() {
        positionRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }

    public List<PositionInfo> fetchPositions(Long userId, FetchPositionsQuery query) {
        return positionMapper.fetchPositions(userId, query);
    }
}
