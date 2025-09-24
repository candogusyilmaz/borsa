package dev.canverse.stocks.service.account;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.repository.CurrencyRepository;
import dev.canverse.stocks.repository.DashboardRepository;
import dev.canverse.stocks.repository.PortfolioRepository;
import dev.canverse.stocks.repository.UserRepository;
import dev.canverse.stocks.service.account.model.UserRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final static String[] ALLOWED_DOMAINS = {
            "gmail.com",
            "yahoo.com",
            "hotmail.com",
            "outlook.com",
            "icloud.com"
    };

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final DashboardRepository dashboardRepository;
    private final CurrencyRepository currencyRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return loadUserByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    public Optional<User> loadUserByEmail(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIncludePermissions(email);
    }

    @Transactional
    public User register(UserRegistrationRequest request) {
        verifyEmailDomain(request.email());
        var normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        var user = new User(normalizedEmail, passwordEncoder.encode(request.password()));
        user.setName(request.name());
        userRepository.save(user);

        var portfolio = portfolioRepository.save(new Portfolio(user, "Portfolio"));

        var dashboard = new Dashboard(user, "Dashboard", currencyRepository.findByCode("TRY"));
        dashboard.setDefault(true);
        dashboard.addPortfolio(portfolio);

        dashboardRepository.save(dashboard);

        return user;
    }

    private void verifyEmailDomain(String email) {
        var domain = email.substring(email.indexOf('@') + 1);

        for (String allowedDomain : ALLOWED_DOMAINS) {
            if (domain.equalsIgnoreCase(allowedDomain)) {
                return;
            }
        }

        throw new IllegalArgumentException("Email domain is not allowed.");
    }

    public void updateLastLogin(Long userId) {
        userRepository.updateLastLoginAtById(userId);
    }
}