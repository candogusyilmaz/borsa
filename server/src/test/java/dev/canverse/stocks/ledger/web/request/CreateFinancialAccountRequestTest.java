package dev.canverse.stocks.ledger.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateFinancialAccountRequestTest {

    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final OpeningStateRequest OPENING_STATE = new OpeningStateRequest("100", Instant.parse("2026-08-17T11:00:00Z"));

    @Test
    void fullLedgerRequiresAnOpeningState() {
        var request = request(TrackingMode.FULL_LEDGER, null);

        assertThatThrownBy(request::validate).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("VALIDATION_FAILED");
            assertThat(exception.getParams().toString()).contains("openingState", "required");
        });
    }

    @Test
    void holdingsOnlyForbidsAnOpeningCashState() {
        var request = request(TrackingMode.HOLDINGS_ONLY, OPENING_STATE);

        assertThatThrownBy(request::validate).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("VALIDATION_FAILED");
            assertThat(exception.getParams().toString()).contains("openingState", "forbidden");
        });
    }

    @Test
    void acceptedTrackingModesAllowTheirMatchingOpeningStateShape() {
        request(TrackingMode.FULL_LEDGER, OPENING_STATE).validate();
        request(TrackingMode.HOLDINGS_ONLY, null).validate();
    }

    private static CreateFinancialAccountRequest request(TrackingMode trackingMode, OpeningStateRequest openingState) {
        return new CreateFinancialAccountRequest(REQUEST_ID, "Request test account", AccountKind.CASH_CURRENT, trackingMode, "USD", "UTC",
                NegativeBalancePolicy.HARD_FLOOR, null, openingState);
    }
}
