package dev.canverse.stocks.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorContractTest {

    @Test
    void commonErrorCodeDerivesCommonMessageKey() {
        assertThat(CommonErrorCode.ENTITY_NOT_FOUND.getMessageKey()).isEqualTo("error.common.entity_not_found");
    }

    @Test
    void domainErrorCodeDerivesOwningDomainMessageKey() {
        assertThat(dev.canverse.stocks.identity.error.IdentityErrorCode.EMAIL_ALREADY_REGISTERED.getMessageKey())
                .isEqualTo("error.identity.email_already_registered");
    }

    @Test
    void appExceptionAcceptsExactRequiredParams() {
        var exception =
                new AppException(CommonErrorCode.ENTITY_NOT_FOUND, Map.of("entity", "Example", "id", "example-1"));

        assertThat(exception.getParams())
                .containsExactlyInAnyOrderEntriesOf(Map.of("entity", "Example", "id", "example-1"));
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    void missingRequiredParamsFailImmediately() {
        assertThatThrownBy(() -> new AppException(CommonErrorCode.ENTITY_NOT_FOUND, Map.of("entity", "Example")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void extraParamsFailImmediately() {
        assertThatThrownBy(() -> new AppException(
                        CommonErrorCode.ENTITY_NOT_FOUND,
                        Map.of("entity", "Example", "id", "example-1", "unexpected", true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appExceptionStoresAnImmutableDeepCopyOfParams() {
        var nested = new ArrayList<>(List.of("first"));
        var supplied = new LinkedHashMap<String, Object>();
        supplied.put("errors", List.of(new LinkedHashMap<>(Map.of("field", nested))));

        var exception = new AppException(CommonErrorCode.VALIDATION_FAILED, supplied);
        nested.add("second");
        supplied.put("other", "not copied");

        assertThat(exception.getParams()).doesNotContainKey("other");
        assertThat(exception.getParams().get("errors").toString())
                .contains("first")
                .doesNotContain("second");
        assertThatThrownBy(() -> exception.getParams().put("errors", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationKeyShapeAcceptsApplicationKeys() {
        assertThat(ValidationKeySupport.isValidApplicationKey("error.fields.common.not_blank"))
                .isTrue();
        assertThat(ValidationKeySupport.isValidApplicationKey("error.fields.identity.password_too_short"))
                .isTrue();
        assertThat(ValidationKeySupport.isValidApplicationKey("error.identity.invalid_credentials"))
                .isTrue();
    }

    @Test
    void malformedValidationKeysAreRejectedAndUseSafeFallback() {
        assertThat(ValidationKeySupport.isValidApplicationKey("{error.fields.common.not_blank}"))
                .isFalse();
        assertThat(ValidationKeySupport.isValidApplicationKey("jakarta.validation.constraints.NotBlank.message"))
                .isFalse();
        assertThat(ValidationKeySupport.isValidApplicationKey("error.fields.Common.not_blank"))
                .isFalse();
        assertThat(ValidationKeySupport.explicitApplicationKey("{not-a-client-key}"))
                .isNull();
        assertThat(ValidationKeySupport.builtInKey("UnknownConstraint")).isNull();
    }
}
