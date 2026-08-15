package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.configuration.AuthenticationAbuseProtectionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class AuthenticationAbuseProtectionPropertiesTest {

    @Test
    void defaultPropertiesAreValidAndPositive() {
        var properties = new AuthenticationAbuseProtectionProperties(null, null, null, null);

        assertThat(properties.login().principalMaxFailures()).isEqualTo(5);
        assertThat(properties.login().sourceMaxFailures()).isEqualTo(25);
        assertThat(properties.login().window()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.login().blockDuration()).isEqualTo(Duration.ofMinutes(15));

        assertThat(properties.registration().sourceMaxAttempts()).isEqualTo(10);
        assertThat(properties.registration().window()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.registration().blockDuration()).isEqualTo(Duration.ofHours(1));

        assertThat(properties.refresh().sourceMaxFailures()).isEqualTo(30);
        assertThat(properties.refresh().window()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.refresh().blockDuration()).isEqualTo(Duration.ofMinutes(15));

        assertThat(properties.maxTrackedKeys()).isEqualTo(10000);
    }

    @Test
    void rejectsZeroAndNegativeMaxTrackedKeys() {
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties(null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTrackedKeys must be positive");

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties(null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTrackedKeys must be positive");
    }

    @Test
    void rejectsZeroAndNegativeLoginProperties() {
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        0, 25, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        -1, 25, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 0, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, -1, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 25, Duration.ZERO, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 25, Duration.ofSeconds(-1), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 25, Duration.ofMinutes(15), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.LoginProperties(
                        5, 25, Duration.ofMinutes(15), Duration.ofSeconds(-10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAndNegativeRegistrationProperties() {
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        0, Duration.ofHours(1), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        -1, Duration.ofHours(1), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        10, Duration.ZERO, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        10, Duration.ofSeconds(-1), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        10, Duration.ofHours(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RegistrationProperties(
                        10, Duration.ofHours(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAndNegativeRefreshProperties() {
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        0, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        -1, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        30, Duration.ZERO, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        30, Duration.ofSeconds(-1), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        30, Duration.ofMinutes(15), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationAbuseProtectionProperties.RefreshProperties(
                        30, Duration.ofMinutes(15), Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExplicitZeroInEnvironmentBinding() {
        assertBindingFails("stocks.identity.abuse-protection.login.principal-max-failures", "0");
        assertBindingFails("stocks.identity.abuse-protection.login.source-max-failures", "0");
        assertBindingFails("stocks.identity.abuse-protection.login.window", "PT0S");
        assertBindingFails("stocks.identity.abuse-protection.login.block-duration", "PT0S");

        assertBindingFails("stocks.identity.abuse-protection.registration.source-max-attempts", "0");
        assertBindingFails("stocks.identity.abuse-protection.registration.window", "PT0S");
        assertBindingFails("stocks.identity.abuse-protection.registration.block-duration", "PT0S");

        assertBindingFails("stocks.identity.abuse-protection.refresh.source-max-failures", "0");
        assertBindingFails("stocks.identity.abuse-protection.refresh.window", "PT0S");
        assertBindingFails("stocks.identity.abuse-protection.refresh.block-duration", "PT0S");

        assertBindingFails("stocks.identity.abuse-protection.max-tracked-keys", "0");
    }

    private void assertBindingFails(String key, String value) {
        var env = new MockEnvironment().withProperty(key, value);
        var binder = Binder.get(env);
        assertThatThrownBy(() ->
                        binder.bind("stocks.identity.abuse-protection", AuthenticationAbuseProtectionProperties.class))
                .isInstanceOf(BindException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
