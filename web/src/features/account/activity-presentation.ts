import type { ActivityType, PolicyDecision, PostingRole, RecordingMode, SecurityPostingRole } from './types';

export function getActivityTypeLabel(type: ActivityType) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return 'Deposit';
    case 'CASH_WITHDRAWAL':
      return 'Withdrawal';
    case 'CASH_FEE':
      return 'Fee';
    case 'CASH_INTEREST_CREDIT':
      return 'Interest';
    case 'OWNED_TRANSFER':
      return 'Transfer';
    case 'OPENING_BALANCE':
      return 'Starting Balance';
    case 'REVERSAL':
      return 'Undone Transaction';
    case 'RECONCILIATION_ADJUSTMENT':
      return 'Balance Adjustment';
    case 'SECURITY_BUY':
      return 'Stock Purchase';
    case 'SECURITY_SELL':
      return 'Stock Sale';
    default:
      return type;
  }
}

export function getActivityTypeDescription(type: ActivityType) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return 'Money deposited into this account.';
    case 'CASH_WITHDRAWAL':
      return 'Money withdrawn from this account.';
    case 'CASH_FEE':
      return 'A fee charged to this account.';
    case 'CASH_INTEREST_CREDIT':
      return 'Interest earned on this account.';
    case 'OWNED_TRANSFER':
      return 'Money moved between your accounts.';
    case 'OPENING_BALANCE':
      return 'Starting balance when account was created.';
    case 'REVERSAL':
      return 'Cancellation undoing a previous transaction.';
    case 'RECONCILIATION_ADJUSTMENT':
      return 'Correction adjusting your balance to match your statement.';
    case 'SECURITY_BUY':
      return 'Purchase of shares or fund units.';
    case 'SECURITY_SELL':
      return 'Sale of shares or fund units.';
    default:
      return '';
  }
}

export function getActivityTypeBadgeColor(type: ActivityType) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return 'teal';
    case 'CASH_WITHDRAWAL':
      return 'orange';
    case 'CASH_FEE':
      return 'red';
    case 'CASH_INTEREST_CREDIT':
      return 'cyan';
    case 'OWNED_TRANSFER':
      return 'blue';
    case 'OPENING_BALANCE':
      return 'indigo';
    case 'REVERSAL':
      return 'violet';
    case 'RECONCILIATION_ADJUSTMENT':
      return 'cyan';
    case 'SECURITY_BUY':
      return 'teal';
    case 'SECURITY_SELL':
      return 'indigo';
    default:
      return 'gray';
  }
}

export function getPostingRoleLabel(role: PostingRole) {
  switch (role) {
    case 'DEPOSIT':
      return 'Deposit';
    case 'WITHDRAWAL':
      return 'Withdrawal';
    case 'FEE':
      return 'Fee';
    case 'INTEREST_CREDIT':
      return 'Interest';
    case 'TRANSFER_SOURCE':
      return 'Transfer Out';
    case 'TRANSFER_DESTINATION':
      return 'Transfer In';
    case 'OPENING':
      return 'Starting Balance';
    case 'REVERSAL':
      return 'Undone Transaction';
    case 'ADJUSTMENT':
      return 'Balance Adjustment';
    case 'TRADE_PURCHASE':
      return 'Stock Purchase';
    case 'TRADE_PROCEEDS':
      return 'Sale Proceeds';
    default:
      return role;
  }
}

export function getSecurityPostingRoleLabel(role: SecurityPostingRole) {
  switch (role) {
    case 'BUY':
      return 'Shares Bought';
    case 'SELL':
      return 'Shares Sold';
    case 'REVERSAL':
      return 'Shares Undone';
    default:
      return role;
  }
}

export function getPolicyDecisionLabel(decision: PolicyDecision) {
  switch (decision) {
    case 'ALLOWED':
      return 'Approved';
    case 'CONFIRMED_BREACH':
      return 'Negative Balance Allowed';
    case 'HISTORICAL_BREACH_RECORDED':
      return 'Past Negative Balance';
    case 'NOT_APPLICABLE':
      return 'Standard';
    default:
      return decision;
  }
}

export function getPolicyDecisionBadgeColor(decision: PolicyDecision) {
  switch (decision) {
    case 'ALLOWED':
      return 'teal';
    case 'CONFIRMED_BREACH':
    case 'HISTORICAL_BREACH_RECORDED':
      return 'red';
    default:
      return 'gray';
  }
}

export function getRecordingModeLabel(mode: RecordingMode) {
  switch (mode) {
    case 'CURRENT_ACTION':
      return 'Right Now (Live)';
    case 'HISTORICAL_FACT':
      return 'Past Date';
    default:
      return mode;
  }
}
