package dev.canverse.stocks.identity.application;

import dev.canverse.stocks.identity.domain.AuthIdentity;
import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.identity.infrastructure.UserAccountRepository;
import dev.canverse.stocks.platform.id.IdGenerator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class LocalAccountRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public LocalAccountRegistrationService(
            UserAccountRepository userAccountRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            IdGenerator idGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public UUID register(
            @NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email,
            @NotBlank @Size(min = 12, max = 128) String rawPassword) {
        var emailNormalized = email.toLowerCase(Locale.ROOT);
        var encodedPasswordHash = passwordEncoder.encode(rawPassword);

        if (userAccountRepository.existsByEmailNormalized(emailNormalized)) {
            throw new EmailAlreadyRegisteredException();
        }

        var registrationTime = clock.instant();
        var userAccountId = idGenerator.next();
        var authIdentityId = idGenerator.next();

        var savedUserAccount = userAccountRepository.save(
                UserAccount.register(userAccountId, email, emailNormalized, registrationTime));

        try {
            authIdentityRepository.saveAndFlush(AuthIdentity.local(
                    authIdentityId, savedUserAccount, emailNormalized, encodedPasswordHash, registrationTime));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateLocalEmailConstraint(exception)) {
                throw new EmailAlreadyRegisteredException(exception);
            }
            throw exception;
        }

        return savedUserAccount.getId();
    }

    private boolean isDuplicateLocalEmailConstraint(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && isDuplicateLocalEmailConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDuplicateLocalEmailConstraint(String constraintName) {
        return "uq_user_account_email_normalized".equals(constraintName)
                || "uq_auth_identity_provider_subject".equals(constraintName);
    }
}
