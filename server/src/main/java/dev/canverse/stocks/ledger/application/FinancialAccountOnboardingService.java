package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.identity.domain.UserAccount;
import dev.canverse.stocks.ledger.domain.AccountBalanceProjection;
import dev.canverse.stocks.ledger.domain.AccountCashPocket;
import dev.canverse.stocks.ledger.domain.Activity;
import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.MoneyPosting;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;
import dev.canverse.stocks.ledger.infrastructure.AccountBalanceProjectionRepository;
import dev.canverse.stocks.ledger.infrastructure.AccountCashPocketRepository;
import dev.canverse.stocks.ledger.infrastructure.ActivityRepository;
import dev.canverse.stocks.ledger.infrastructure.FinancialAccountRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerCommandLockRepository;
import dev.canverse.stocks.ledger.infrastructure.LedgerReadRepository;
import dev.canverse.stocks.ledger.infrastructure.MoneyPostingRepository;
import dev.canverse.stocks.ledger.web.request.CreateFinancialAccountRequest;
import dev.canverse.stocks.ledger.web.response.FinancialAccountResponse;
import dev.canverse.stocks.platform.application.CanonicalFingerprint;
import dev.canverse.stocks.platform.error.AppException;
import dev.canverse.stocks.platform.error.ValidationErrors;
import dev.canverse.stocks.platform.id.IdGenerator;
import dev.canverse.stocks.reference.infrastructure.CurrencyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialAccountOnboardingService {

    private final EntityManager entityManager;
    private final FinancialAccountRepository accountRepository;
    private final LedgerCommandLockRepository commandLockRepository;
    private final LedgerReadRepository readRepository;
    private final CurrencyRepository currencyRepository;
    private final AccountCashPocketRepository pocketRepository;
    private final ActivityRepository activityRepository;
    private final MoneyPostingRepository postingRepository;
    private final AccountBalanceProjectionRepository projectionRepository;
    private final LedgerIdempotencyStore idempotencyStore;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CanonicalFingerprint fingerprint;

    @Transactional
    public FinancialAccountResponse create(UUID ownerUserAccountId, CreateFinancialAccountRequest request) {
        Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        Objects.requireNonNull(request, "request");

        var currency = Objects.requireNonNull(request.currency(), "currency").trim().toUpperCase(Locale.ROOT);
        var opening = request.openingState();
        var openingAmount = opening == null ? null : LedgerAmountParser.exact(opening.amount(), "openingState.amount");
        var observedAt = clock.instant();
        if (opening != null) {
            if (Objects.requireNonNull(opening.effectiveAt(), "openingState.effectiveAt").isAfter(observedAt)) {
                throw new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED);
            }
        }
        var timeZone = validateTimeZone(request.timeZone());
        var authorizedLimit = LedgerAmountParser.optional(request.authorizedLimit(), "authorizedLimit");
        var requestHash = requestHash(request, currency, timeZone, authorizedLimit, openingAmount);
        commandLockRepository.lock(ownerUserAccountId, LedgerCommandScopes.ACCOUNT_CREATE, request.clientRequestId());
        var replay = idempotencyStore.replay(request.clientRequestId(), ownerUserAccountId, LedgerCommandScopes.ACCOUNT_CREATE, requestHash,
                FinancialAccountResponse.class);
        if (replay != null) {
            return replay;
        }

        validateCurrency(currency);

        var account = createAccount(ownerUserAccountId, request, currency, timeZone, authorizedLimit, observedAt);
        if (request.trackingMode() == TrackingMode.FULL_LEDGER) {
            var openingState = request.openingState();
            var openingActivity = writeOpening(ownerUserAccountId, account, openingAmount, openingState.effectiveAt(), request.clientRequestId(), observedAt);
            account.setCurrentOpeningActivity(openingActivity.getId());
        }

        entityManager.flush();
        var response = readAccount(ownerUserAccountId, account.getId());
        idempotencyStore.save(ownerUserAccountId, LedgerCommandScopes.ACCOUNT_CREATE, request.clientRequestId(), requestHash, "FINANCIAL_ACCOUNT",
                account.getId(), response, observedAt);
        return response;
    }

    private Activity writeOpening(UUID ownerUserAccountId, FinancialAccount account, FinancialAmount openingAmount, Instant effectiveAt, UUID clientRequestId,
            Instant observedAt) {
        var pocket = AccountCashPocket.create(idGenerator.next(), ownerUserAccountId, account, account.getCurrencyCode(), effectiveAt, observedAt);
        pocketRepository.save(pocket);

        var openingDecision = openingAmount.isNegative() ? PolicyDecision.HISTORICAL_BREACH_RECORDED : PolicyDecision.ALLOWED;
        var openingActivity = Activity.openingBalance(idGenerator.next(), ownerUserAccountId, clientRequestId, LedgerCommandScopes.ACCOUNT_CREATE, 0,
                effectiveAt, observedAt, openingDecision);
        activityRepository.save(openingActivity);
        postingRepository.save(MoneyPosting.opening(idGenerator.next(), ownerUserAccountId, openingActivity.getId(), account.getId(), pocket.getId(),
                account.getCurrencyCode(), openingAmount, observedAt));
        projectionRepository.save(AccountBalanceProjection.create(idGenerator.next(), ownerUserAccountId, account, pocket, account.getCurrencyCode(),
                openingAmount, observedAt, openingActivity.getId(), observedAt));
        return openingActivity;
    }

    private String requestHash(CreateFinancialAccountRequest request, String currency, String timeZone, FinancialAmount authorizedLimit,
            FinancialAmount openingAmount) {
        var opening = request.openingState();
        return fingerprint.hash(fingerprint.values("name", request.name().trim(), "kind", request.kind().name(), "trackingMode", request.trackingMode().name(),
                "currency", currency, "timeZone", timeZone, "policy", request.policy() == null ? null : request.policy().name(), "authorizedLimit",
                authorizedLimit == null ? null : authorizedLimit.canonical(), "openingAmount", openingAmount == null ? null : openingAmount.canonical(),
                "openingEffectiveAt", opening == null ? null : opening.effectiveAt().toString()));
    }

    private FinancialAccount createAccount(UUID ownerUserAccountId, CreateFinancialAccountRequest request, String currency, String timeZone,
            FinancialAmount authorizedLimit, Instant observedAt) {
        var owner = entityManager.getReference(UserAccount.class, ownerUserAccountId);
        FinancialAccount account;
        try {
            account = FinancialAccount.create(idGenerator.next(), owner, request.name(), request.kind(), request.trackingMode(), currency, timeZone,
                    request.policy(), authorizedLimit, observedAt);
        } catch (IllegalArgumentException exception) {
            throw new AppException(LedgerErrorCode.ACCOUNT_ACTION_NOT_SUPPORTED, exception);
        }
        return accountRepository.save(account);
    }

    private FinancialAccountResponse readAccount(UUID ownerUserAccountId, UUID accountId) {
        return readRepository.findAccount(ownerUserAccountId, accountId).map(FinancialAccountResponse::from)
                .orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateCurrency(String currency) {
        var entity = currencyRepository.findById(currency).orElseThrow(() -> new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED));
        if (!entity.isActive()) {
            throw new AppException(LedgerErrorCode.ACCOUNT_CURRENCY_UNSUPPORTED);
        }
    }

    private static String validateTimeZone(String timeZone) {
        try {
            return FinancialAccount.requireIanaTimeZone(timeZone);
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField("timeZone", "error.fields.ledger.invalid_timezone", "The time zone must be an IANA zone.");
        }
    }
}
