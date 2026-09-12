import type { FinancialAccount } from '../../types';
import { formatCurrency, isCashFundingCapable, isLiabilityKind, toDatetimeLocal } from '../../utils/account-formatters';
import type { TransferDefaultsOptions, TransferFormValues, TransferPolicyPresentation, TransferPreviewResponse } from './transfer-types';

/**
 * Clock safety buffer (1 second) to prevent backend rejected FUTURE_TIME_NOT_ALLOWED
 * errors due to minor client-server clock skew on real-time execution.
 */
export const CURRENT_ACTION_CLOCK_SAFETY_MS = 1_000;

/**
 * Filters all accounts down to those eligible to participate in cash transfers.
 * Accounts must be active (non-archived), Full Ledger tracked, non-liability,
 * and funding-capable cash assets.
 */
export function getEligibleTransferAccounts(accounts: FinancialAccount[]): FinancialAccount[] {
  return accounts.filter(
    (account) =>
      !account.archived && account.trackingMode === 'FULL_LEDGER' && !isLiabilityKind(account.kind) && isCashFundingCapable(account.kind)
  );
}

/**
 * Derives eligible destination accounts for a selected source account.
 * Transfer rules require accounts to share the exact same currency and not be identical.
 */
export function getDestinationAccounts(accounts: FinancialAccount[], sourceAccountId: string): FinancialAccount[] {
  const source = accounts.find((account) => account.id === sourceAccountId);
  if (!source) {
    return [];
  }

  return accounts.filter((account) => account.id !== source.id && account.currency === source.currency);
}

/**
 * Calculates deterministic initial source and destination accounts
 * based on provided defaults, page locking constraints, and available accounts.
 */
export function resolveInitialTransferAccounts(options: TransferDefaultsOptions): {
  sourceAccountId: string;
  destinationAccountId: string;
} {
  const { accounts, defaultSourceAccountId, defaultDestinationAccountId, lockSourceAccount } = options;
  if (accounts.length === 0) {
    return { sourceAccountId: '', destinationAccountId: '' };
  }

  let resolvedSourceId = '';
  if (lockSourceAccount && defaultSourceAccountId && accounts.some((a) => a.id === defaultSourceAccountId)) {
    resolvedSourceId = defaultSourceAccountId;
  } else if (defaultSourceAccountId && accounts.some((a) => a.id === defaultSourceAccountId)) {
    resolvedSourceId = defaultSourceAccountId;
  } else {
    const partnerCandidate = accounts.find((acc) => getDestinationAccounts(accounts, acc.id).length > 0);
    resolvedSourceId = partnerCandidate?.id ?? accounts[0]?.id ?? '';
  }

  const compatibleDestinations = getDestinationAccounts(accounts, resolvedSourceId);

  let resolvedDestId = '';
  if (
    defaultDestinationAccountId &&
    defaultDestinationAccountId !== resolvedSourceId &&
    compatibleDestinations.some((a) => a.id === defaultDestinationAccountId)
  ) {
    resolvedDestId = defaultDestinationAccountId;
  } else if (compatibleDestinations.length > 0 && compatibleDestinations[0]) {
    resolvedDestId = compatibleDestinations[0].id;
  }

  return {
    sourceAccountId: resolvedSourceId,
    destinationAccountId: resolvedDestId
  };
}

/**
 * Creates default transfer form values with safe initialized defaults.
 */
export function createTransferFormDefaults(options: { sourceAccountId: string; destinationAccountId: string }): TransferFormValues {
  return {
    sourceAccountId: options.sourceAccountId,
    destinationAccountId: options.destinationAccountId,
    amount: '',
    recordingMode: 'CURRENT_ACTION',
    effectiveAt: toDatetimeLocal(new Date()),
    confirmPolicyBreach: false
  };
}

/**
 * Computes an ISO 8601 effective timestamp from form values, applying
 * clock-skew protection for real-time mode or parsing selected historical datetime.
 */
export function resolveEffectiveAt(values: TransferFormValues): string {
  if (values.recordingMode === 'HISTORICAL_FACT') {
    return new Date(values.effectiveAt).toISOString();
  }

  return new Date(Date.now() - CURRENT_ACTION_CLOCK_SAFETY_MS).toISOString();
}

/**
 * Interprets backend simulation decisions and policies into user-facing presentation models.
 */
export function getTransferPolicyPresentation(
  preview: TransferPreviewResponse,
  source?: FinancialAccount
): TransferPolicyPresentation | null {
  if (preview.sourceDecision === 'CONFIRMED_BREACH') {
    return {
      severity: 'warning',
      title: 'Overdraft Notice Confirmed',
      description: `This transfer will result in a negative balance on ${source?.name || 'the source account'}. Because overdraft confirmation is granted, this transaction will execute as an authorized policy breach.`
    };
  }

  if (preview.sourceDecision === 'HISTORICAL_BREACH_RECORDED') {
    return {
      severity: 'info',
      title: 'Historical Overdraft Entry',
      description: 'This transaction is backdated and recorded as a historical fact. The ledger will mark a historical overdraft watermark.'
    };
  }

  if (!preview.allowed) {
    const isNegativeAfter = preview.sourceAfter.startsWith('-');
    const policy = source?.policy;

    if (policy === 'SOFT_FLOOR') {
      const overdraftDeficit = isNegativeAfter ? preview.sourceAfter.slice(1) : preview.amount;
      return {
        severity: 'warning',
        title: 'Overdraft Confirmation Required',
        description: `This transfer will overdraw ${source?.name || 'the source account'} by ${formatCurrency(overdraftDeficit, preview.currency)}. Your account policy permits overdrafts when explicitly confirmed.`,
        requiresConfirmation: true
      };
    }

    if (policy === 'HARD_FLOOR') {
      return {
        severity: 'error',
        title: 'Transfer Blocked: Strict Zero Minimum Policy',
        description: `${source?.name || 'The source account'} has a strict zero minimum policy. Transactions causing negative balances are prohibited.`
      };
    }

    if (policy === 'AUTHORIZED_LIMIT') {
      return {
        severity: 'error',
        title: 'Transfer Blocked: Authorized Overdraft Limit Exceeded',
        description: `The resulting negative balance exceeds the authorized credit overdraft limit configured on ${source?.name || 'the source account'}.`
      };
    }

    return {
      severity: 'error',
      title: 'Transfer Blocked by Account Policy',
      description: 'The source account policy prevents this transfer because it lacks sufficient funds.'
    };
  }

  return null;
}
