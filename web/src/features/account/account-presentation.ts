import type { AccountKind, TrackingMode } from './types';

export function getAccountKindLabel(kind: AccountKind) {
  switch (kind) {
    case 'CASH_CURRENT':
      return 'Current (Checking)';
    case 'CASH_SAVINGS':
      return 'Savings Account';
    case 'CASH_WALLET':
      return 'Cash Wallet';
    case 'BROKERAGE':
      return 'Brokerage / Investment';
    case 'CREDIT_CARD':
      return 'Credit Card';
    case 'LOAN':
      return 'Loan / Debt';
    default:
      return kind;
  }
}

export function getAccountKindDescription(kind: AccountKind) {
  switch (kind) {
    case 'CASH_CURRENT':
      return 'Everyday checking account for deposits, withdrawals, and transfers.';
    case 'CASH_SAVINGS':
      return 'Savings account to hold reserve funds and earn interest.';
    case 'CASH_WALLET':
      return 'Physical cash in hand or digital petty cash wallet.';
    case 'BROKERAGE':
      return 'Investment account for buying and selling stocks, ETFs, and funds.';
    case 'CREDIT_CARD':
      return 'Credit card account to track purchases, credit line, and repayments.';
    case 'LOAN':
      return 'Loan or debt account to track borrowed money and repayments.';
    default:
      return '';
  }
}

export function getAccountKindBadgeColor(kind: AccountKind) {
  switch (kind) {
    case 'CASH_CURRENT':
      return 'teal';
    case 'CASH_SAVINGS':
      return 'cyan';
    case 'CASH_WALLET':
      return 'blue';
    case 'BROKERAGE':
      return 'grape';
    case 'CREDIT_CARD':
      return 'orange';
    case 'LOAN':
      return 'red';
    default:
      return 'gray';
  }
}

export function getTrackingModeLabel(mode: TrackingMode) {
  switch (mode) {
    case 'FULL_LEDGER':
      return 'Full Ledger';
    case 'HOLDINGS_ONLY':
      return 'Holdings Only';
    default:
      return mode;
  }
}

export function getTrackingModeDescription(mode: TrackingMode) {
  switch (mode) {
    case 'FULL_LEDGER':
      return 'Full accounting that tracks all cash deposits, withdrawals, and balances.';
    case 'HOLDINGS_ONLY':
      return 'Simple portfolio tracking for stock quantities without cash bookkeeping.';
    default:
      return '';
  }
}

export function getTrackingModeBadgeColor(mode: TrackingMode) {
  return mode === 'FULL_LEDGER' ? 'brand' : 'indigo';
}

export function getPolicyLabel(policy?: string) {
  if (!policy) return 'None (Liability / Untracked)';
  switch (policy) {
    case 'HARD_FLOOR':
      return 'Hard Floor (Zero Min)';
    case 'SOFT_FLOOR':
      return 'Soft Floor (Warning)';
    case 'TRACK_REALITY':
      return 'Track Reality (Unrestricted)';
    case 'AUTHORIZED_LIMIT':
      return 'Authorized Overdraft Limit';
    default:
      return policy;
  }
}

export function getCoverageStatusPresentation(status?: string) {
  switch (status) {
    case 'KNOWN_FROM_OPENING':
      return { label: 'Known from opening', badgeLabel: 'Verified', isVerified: true };
    case 'ESTIMATED':
      return { label: 'Estimated starting balance', badgeLabel: 'Estimated', isVerified: false };
    case 'UNKNOWN':
      return { label: 'Unverified starting history', badgeLabel: 'Unverified', isVerified: false };
    case 'INCOMPLETE':
      return { label: 'Incomplete cash coverage', badgeLabel: 'Incomplete', isVerified: false };
    default:
      return {
        label: status ? status.replace(/_/g, ' ').toLowerCase() : 'Not established',
        badgeLabel: status ? 'Active' : 'Unverified',
        isVerified: status === 'KNOWN_FROM_OPENING'
      };
  }
}

export function getPolicyDescription(policy?: string) {
  if (!policy) return 'No policy enforced on this account.';
  switch (policy) {
    case 'HARD_FLOOR':
      return 'Strict zero minimum. Transactions that would overdraw are blocked.';
    case 'SOFT_FLOOR':
      return 'Allows overdraft with a warning alert when balance drops below zero.';
    case 'TRACK_REALITY':
      return 'Unrestricted balance. Records all transactions without overdraft blocks.';
    case 'AUTHORIZED_LIMIT':
      return 'Allows overdraft down to your pre-approved credit limit.';
    default:
      return '';
  }
}
