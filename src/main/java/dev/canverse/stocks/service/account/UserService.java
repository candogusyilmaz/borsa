package dev.canverse.stocks.service.account;

import dev.canverse.stocks.domain.entity.account.User;
import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import dev.canverse.stocks.domain.entity.portfolio.Portfolio;
import dev.canverse.stocks.repository.CurrencyRepository;
import dev.canverse.stocks.repository.UserRepository;
import dev.canverse.stocks.service.account.model.UserRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
    private final CurrencyRepository currencyRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return loadUserByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    public Optional<User> loadUserByEmail(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIncludePermissions(email);
    }

    public User createUser(String name, String email, String password) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        var user = new User(email, password);
        user.setName(name);

        var dashboard = new Dashboard(user, "Dashboard", currencyRepository.findByCode("TRY"));
        dashboard.setDefault(true);
        dashboard.addPortfolio(new Portfolio(user, "Portfolio"));
        user.addDashboard(dashboard);

        return userRepository.save(user);
    }

    public User register(UserRegistrationRequest request) {
        verifyEmailDomain(request.email());

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        var user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.setName(request.name());

        var dashboard = new Dashboard(user, "Dashboard", currencyRepository.findByCode("TRY"));
        dashboard.setDefault(true);
        dashboard.addPortfolio(new Portfolio(user, "Portfolio"));
        user.addDashboard(dashboard);

        return userRepository.save(user);
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