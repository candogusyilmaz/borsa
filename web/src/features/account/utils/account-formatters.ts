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

export const PLAIN_DECIMAL_REGEX = /^-?(?:0|[1-9]\d*)(?:\.\d+)?$/;

export function toDatetimeLocal(date: Date = new Date(), includeSeconds = false): string {
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => n.toString().padStart(2, '0');
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());
  if (includeSeconds) {
    const seconds = pad(date.getSeconds());
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
  }
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

export function toRelativeTime(isoString?: string): string {
  if (!isoString) return '';
  try {
    const timestamp = new Date(isoString).getTime();
    if (Number.isNaN(timestamp)) return '';
    const diffMs = Date.now() - timestamp;
    const diffSec = Math.floor(diffMs / 1000);
    if (diffSec < 60) return 'just now';
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 30) return `${diffDays}d ago`;
    const diffMonths = Math.floor(diffDays / 30);
    if (diffMonths < 12) return `${diffMonths}mo ago`;
    const diffYears = Math.floor(diffDays / 365);
    return `${diffYears}y ago`;
  } catch {
    return '';
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
