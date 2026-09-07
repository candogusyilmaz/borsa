package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.AuthIdentity;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.id.IdGenerator;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalAccountRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public LocalAccountRegistrationService(UserAccountRepository userAccountRepository, AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder, Clock clock, IdGenerator idGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public UUID register(String email, String rawPassword) {
        var emailNormalized = email.toLowerCase(Locale.ROOT);
        var encodedPasswordHash = passwordEncoder.encode(rawPassword);

        if (userAccountRepository.existsByEmailNormalized(emailNormalized)) {
            throw new AppException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        var registrationTime = clock.instant();
        var userAccountId = idGenerator.next();
        var authIdentityId = idGenerator.next();

        var savedUserAccount = userAccountRepository.save(UserAccount.register(userAccountId, email, emailNormalized, registrationTime));

        authIdentityRepository.save(AuthIdentity.local(authIdentityId, savedUserAccount, emailNormalized, encodedPasswordHash, registrationTime));

        return savedUserAccount.getId();
    }
}
