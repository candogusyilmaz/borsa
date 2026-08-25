package dev.canverse.stocks.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.application.LedgerCursorCodec;
import dev.canverse.stocks.ledger.application.model.AccountCursor;
import dev.canverse.stocks.ledger.application.model.ActivityCursor;
import dev.canverse.stocks.ledger.application.model.ReconciliationCursor;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.application.CursorTokenCodec;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LedgerCursorCodecTest {

    private final LedgerCursorCodec codec = new LedgerCursorCodec(
            new CursorTokenCodec(),
            new CanonicalFingerprint(JsonMapper.builder().build()),
            JsonMapper.builder().build());

    @Test
    void accountActivityAndReconciliationCursorsRoundTripAsCanonicalBase64UrlJson() {
        var filterDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        var account = new AccountCursor(
                filterDigest, "ACCOUNT \"\\\n", UUID.fromString("10000000-0000-0000-0000-000000000001"));
        var activity = new ActivityCursor(
                filterDigest,
                Instant.parse("2026-08-16T09:00:00Z"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"));
        var reconciliation = new ReconciliationCursor(
                filterDigest,
                Instant.parse("2026-08-16T10:00:00Z"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"));

        var encodedAccount = codec.encodeAccount(account);
        var encodedActivity = codec.encodeActivity(activity);
        var encodedReconciliation = codec.encodeReconciliation(reconciliation);

        assertThat(codec.decodeAccount(encodedAccount)).isEqualTo(account);
        assertThat(codec.decodeActivity(encodedActivity)).isEqualTo(activity);
        assertThat(codec.decodeReconciliation(encodedReconciliation)).isEqualTo(reconciliation);
        assertThat(encodedAccount).doesNotContain("=").matches("[A-Za-z0-9_-]+");
        assertThat(encodedActivity).doesNotContain("=").matches("[A-Za-z0-9_-]+");
        assertThat(encodedReconciliation).doesNotContain("=").matches("[A-Za-z0-9_-]+");
        assertInvalidCursor(() -> codec.decodeAccount(encodedAccount, "f".repeat(64)));
        assertInvalidCursor(() -> codec.decodeActivity(encodedActivity, "f".repeat(64)));
        assertInvalidCursor(() -> codec.decodeReconciliation(encodedReconciliation, "f".repeat(64)));
    }

    @Test
    void malformedCursorsAreRejected() {
        assertInvalidCursor(() -> codec.decodeAccount("not-a-cursor"));
        assertInvalidCursor(() -> codec.decodeActivity("not-a-cursor"));
        assertInvalidCursor(() -> codec.decodeReconciliation("not-a-cursor"));
    }

    @Test
    void cursorPayloadMustUseTheCurrentSchemaAndCanonicalValues() {
        var filterDigest = "0".repeat(64);
        var accountId = "a0000000-0000-0000-0000-000000000001";
        var validPayload = "{\"v\":1,\"f\":\"%s\",\"n\":\"ACCOUNT\",\"i\":\"%s\"}".formatted(filterDigest, accountId);

        assertInvalidCursor(() -> codec.decodeAccount(base64(validPayload.replace("}", ",\"x\":true}"))));
        assertInvalidCursor(() -> codec.decodeAccount(base64(validPayload.replace("\"v\":1", "\"v\":2"))));
        assertInvalidCursor(
                () -> codec.decodeAccount(base64(validPayload.replace(accountId, accountId.toUpperCase()))));
    }

    private static void assertInvalidCursor(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
            assertThat(exception.getParams())
                    .containsEntry(
                            "errors",
                            List.of(Map.of(
                                    "field", "cursor",
                                    "key", "error.fields.ledger.invalid_cursor",
                                    "detail", "The cursor is invalid.")));
        });
    }

    private static String base64(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
