package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.FinancialAccount;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.error.LedgerErrorCode;

/**
 * Evaluates the policy attached to an account against a proposed balance change.
 */
final class LedgerPolicyEvaluator {

    private LedgerPolicyEvaluator() {}

    static PolicyEvaluation evaluate(FinancialAccount account, FinancialAmount current, FinancialAmount delta, RecordingMode recordingMode,
            boolean confirmPolicyBreach) {
        var after = current.add(delta);
        if (recordingMode == RecordingMode.HISTORICAL_FACT) {
            return new PolicyEvaluation(true, after.isNegative() ? PolicyDecision.HISTORICAL_BREACH_RECORDED : PolicyDecision.ALLOWED, null);
        }
        var policy = account.getNegativeBalancePolicy();
        if (!after.isNegative()) {
            return new PolicyEvaluation(true, PolicyDecision.ALLOWED, null);
        }
        if (policy == NegativeBalancePolicy.HARD_FLOOR) {
            return new PolicyEvaluation(false, PolicyDecision.NOT_APPLICABLE, LedgerErrorCode.INSUFFICIENT_FUNDS);
        }
        if (policy == NegativeBalancePolicy.AUTHORIZED_LIMIT) {
            var limit = account.authorizedLimitAmount();
            if (after.compareTo(limit.negate()) < 0) {
                return new PolicyEvaluation(false, PolicyDecision.NOT_APPLICABLE, LedgerErrorCode.ACCOUNT_LIMIT_EXCEEDED);
            }
            return new PolicyEvaluation(true, PolicyDecision.ALLOWED, null);
        }
        if (policy == NegativeBalancePolicy.SOFT_FLOOR && !confirmPolicyBreach) {
            return new PolicyEvaluation(false, PolicyDecision.NOT_APPLICABLE, LedgerErrorCode.POLICY_BREACH_CONFIRMATION_REQUIRED);
        }
        return new PolicyEvaluation(true,
                policy == NegativeBalancePolicy.SOFT_FLOOR ? PolicyDecision.CONFIRMED_BREACH : PolicyDecision.HISTORICAL_BREACH_RECORDED, null);
    }

    record PolicyEvaluation(boolean allowed, PolicyDecision decision, LedgerErrorCode errorCode) {}
}
