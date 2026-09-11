import type { AccountKind, NegativeBalancePolicy, TrackingMode } from '../types';

export function isLiabilityKind(kind: AccountKind) {
  return kind === 'CREDIT_CARD' || kind === 'LOAN';
}

export function isAssetKind(kind: AccountKind) {
  return !isLiabilityKind(kind);
}

export function supportsHoldingsOnly(kind: AccountKind) {
  return kind === 'BROKERAGE';
}

export function supportsNegativePolicy(kind: AccountKind, policy: NegativeBalancePolicy) {
  if (isLiabilityKind(kind)) {
    return false;
  }
  if (kind === 'CASH_CURRENT') {
    return true;
  }
  return policy !== 'AUTHORIZED_LIMIT';
}

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
      return 'Operating cash account for deposits, withdrawals, and transfers. Supports overdraft limit.';
    case 'CASH_SAVINGS':
      return 'Reserve funds account for yield and savings allocations.';
    case 'CASH_WALLET':
      return 'Physical cash pocket or digital liquidity wallet.';
    case 'BROKERAGE':
      return 'Securities trading account for stocks, funds, and ETFs. Supports full ledger or holdings-only mode.';
    case 'CREDIT_CARD':
      return 'Revolving credit liability account.';
    case 'LOAN':
      return 'Fixed-term or margin loan liability account.';
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
      return 'Strict double-entry bookkeeping with opening state, transactions, and cash pocket invariants.';
    case 'HOLDINGS_ONLY':
      return 'Position-only tracking for stock quantities without cash ledger balancing.';
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

export function getPolicyDescription(policy?: string) {
  if (!policy) return 'No policy enforced on this account.';
  switch (policy) {
    case 'HARD_FLOOR':
      return 'Outflows that would cause balance to drop below zero are strictly rejected.';
    case 'SOFT_FLOOR':
      return 'Allows negative balance with breach notification warnings.';
    case 'TRACK_REALITY':
      return 'Accepts all transactions as reality without blocking on deficits.';
    case 'AUTHORIZED_LIMIT':
      return 'Allows negative balance down to an explicit agreed overdraft limit.';
    default:
      return '';
  }
}

export function formatCurrency(amount: string | number | null | undefined, currency: string = 'USD') {
  if (amount === null || amount === undefined || amount === '') {
    return '—';
  }
  const numeric = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (Number.isNaN(numeric)) {
    return String(amount);
  }

  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(numeric);
  } catch {
    return `${numeric.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
  }
}

export function formatDate(isoString?: string) {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return isoString;
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    }).format(date);
  } catch {
    return isoString;
  }
}

export function formatDateTime(isoString?: string) {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (Number.isNaN(date.getTime())) return isoString;
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      hour12: true
    }).format(date);
  } catch {
    return isoString;
  }
}

export const COMMON_TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Toronto',
  'America/Sao_Paulo',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Istanbul',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Asia/Hong_Kong',
  'Australia/Sydney'
];

export const COMMON_CURRENCIES = [
  { code: 'USD', name: 'US Dollar', symbol: '$' },
  { code: 'EUR', name: 'Euro', symbol: '€' },
  { code: 'TRY', name: 'Turkish Lira', symbol: '₺' },
  { code: 'GBP', name: 'British Pound', symbol: '£' },
  { code: 'CHF', name: 'Swiss Franc', symbol: 'CHF' },
  { code: 'JPY', name: 'Japanese Yen', symbol: '¥' },
  { code: 'CAD', name: 'Canadian Dollar', symbol: '$' },
  { code: 'AUD', name: 'Australian Dollar', symbol: '$' }
];
