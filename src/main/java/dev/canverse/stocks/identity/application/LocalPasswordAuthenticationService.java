package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.AuthIdentity;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalPasswordAuthenticationService {

    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final String DUMMY_PASSWORD = "local-password-authentication-dummy";

    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyEncodedHash;

    public LocalPasswordAuthenticationService(
            AuthIdentityRepository authIdentityRepository, PasswordEncoder passwordEncoder) {
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyEncodedHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional(readOnly = true)
    public UUID authenticate(String email, String rawPassword) {
        var normalizedEmail = email.toLowerCase(Locale.ROOT);
        var localIdentity = authIdentityRepository.findByProviderAndProviderSubject(LOCAL_PROVIDER, normalizedEmail);
        var storedHash = localIdentity.map(AuthIdentity::getPasswordHash).orElse(null);
        var hashForMatch = storedHash == null ? dummyEncodedHash : storedHash;
        var passwordMatches = passwordEncoder.matches(rawPassword, hashForMatch);

        if (storedHash == null || !passwordMatches) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        var userAccount = localIdentity
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_CREDENTIALS))
                .getUserAccount();
        if (userAccount.getDisabledAt() != null) {
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        return userAccount.getId();
    }
}
