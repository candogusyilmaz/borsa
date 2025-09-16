package dev.canverse.stocks.service.portfolio;


import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.domain.exception.NotFoundException;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.security.AuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioAccessValidator {
    private final PortfolioRepository portfolioRepository;

    public Portfolio validateAccess(Long portfolioId) {
        return validateAccess(portfolioId, AuthenticationProvider.getUser().getId());
    }

    public Portfolio validateAccess(Long portfolioId, Long userId) {
        var portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        if (!portfolio.getUser().getId().equals(userId)) {
            throw new NotFoundException("Portfolio not found");
        }

        if (portfolio.isArchived()) {
            throw new NotFoundException("Portfolio is archived");
        }

        return portfolio;
    }
}