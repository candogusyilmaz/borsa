package dev.canverse.stocks.service.account;

import dev.canverse.stocks.domain.entity.User;
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
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

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
        return userRepository.save(user);
    }

    public User register(UserRegistrationRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        var user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.setName(request.name());
        return userRepository.save(user);
    }
}