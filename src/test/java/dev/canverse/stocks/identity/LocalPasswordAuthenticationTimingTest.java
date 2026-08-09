package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.canverse.stocks.identity.application.LocalPasswordAuthenticationService;
import dev.canverse.stocks.identity.error.IdentityErrorCode;
import dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository;
import dev.canverse.stocks.platform.error.AppException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalPasswordAuthenticationTimingTest {

    private static final String DUMMY_PASSWORD = "local-password-authentication-dummy";
    private static final String DUMMY_HASH = "{bcrypt}dummy-encoded-hash";
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @Test
    void unknownEmailUsesOneConstructedDummyHashForEachMatchAttempt() {
        var repository = mock(AuthIdentityRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        when(repository.findByProviderAndProviderSubject("LOCAL", "missing@example.com"))
                .thenReturn(Optional.empty());
        when(repository.findByProviderAndProviderSubject("LOCAL", "another@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(DUMMY_PASSWORD)).thenReturn(DUMMY_HASH);

        var service = new LocalPasswordAuthenticationService(repository, passwordEncoder);

        var firstAttempt = catchThrowable(() -> service.authenticate("Missing@Example.com", RAW_PASSWORD));
        var secondAttempt = catchThrowable(() -> service.authenticate("Another@Example.com", RAW_PASSWORD));

        assertCredentialFailure(firstAttempt, "Missing@Example.com", RAW_PASSWORD);
        assertCredentialFailure(secondAttempt, "Another@Example.com", RAW_PASSWORD);
        assertThat(IdentityErrorCode.INVALID_CREDENTIALS.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(IdentityErrorCode.INVALID_CREDENTIALS.getRequiredParams()).isEmpty();
        assertThat(IdentityErrorCode.INVALID_CREDENTIALS.getMessageKey())
                .isEqualTo("error.identity.invalid_credentials");

        verify(passwordEncoder, times(1)).encode(DUMMY_PASSWORD);
        verify(passwordEncoder, never()).encode(RAW_PASSWORD);
        verify(passwordEncoder, times(2)).matches(RAW_PASSWORD, DUMMY_HASH);
        verify(repository).findByProviderAndProviderSubject("LOCAL", "missing@example.com");
        verify(repository).findByProviderAndProviderSubject("LOCAL", "another@example.com");
    }

    private void assertCredentialFailure(Throwable thrown, String email, String rawPassword) {
        assertThat(thrown).isExactlyInstanceOf(AppException.class);
        var exception = (AppException) thrown;
        assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        assertThat(exception.getParams()).isEmpty();
        assertThat(exception.getMessage()).isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS.getDescription());
        assertThat(exception.toString()).doesNotContain(email, rawPassword);
    }
}
