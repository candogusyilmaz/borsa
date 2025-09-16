package dev.canverse.stocks.service.portfolio;

import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.PositionRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;

    @Transactional
    public void deleteAllPositions() {
        positionRepository.deleteAllByUserId(AuthenticationProvider.getUser().getId());
    }
}
