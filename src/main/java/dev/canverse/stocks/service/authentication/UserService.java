package dev.canverse.stocks.service.authentication;

import dev.canverse.stocks.domain.entity.User;
import dev.canverse.stocks.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIncludePermissions(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    public Optional<User> loadUserByEmail(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIncludePermissions(email);
    }

    public User createUser(String username, String name, String email, String password) {
        var user = new User(username, password, email);
        user.setName(name);
        return userRepository.save(user);
    }
}