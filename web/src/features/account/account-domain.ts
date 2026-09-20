import type { AccountKind, NegativeBalancePolicy } from './types';

export function isLiabilityKind(kind: AccountKind) {
  return kind === 'CREDIT_CARD' || kind === 'LOAN';
}

export function isAssetKind(kind: AccountKind) {
  return !isLiabilityKind(kind);
}

export function supportsHoldingsOnly(kind: AccountKind) {
  return kind === 'BROKERAGE';
}

export function isCashFundingCapable(kind: AccountKind) {
  return kind === 'CASH_CURRENT' || kind === 'CASH_SAVINGS' || kind === 'CASH_WALLET' || kind === 'BROKERAGE';
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
