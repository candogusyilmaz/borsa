import type { components } from '@/api/schema';

export type FinancialAccount = components['schemas']['FinancialAccountResponse'];
export type BalanceResponse = components['schemas']['BalanceResponse'];
export type AccountKind = FinancialAccount['kind'];
export type TrackingMode = FinancialAccount['trackingMode'];
export type NegativeBalancePolicy = NonNullable<FinancialAccount['policy']>;
export type CurrencyResponse = components['schemas']['CurrencyResponse'];

export type AccountModeFilter = 'ALL' | 'FULL_LEDGER' | 'HOLDINGS_ONLY' | 'ASSET' | 'LIABILITY';

export interface CreateAccountFormValues {
  name: string;
  kind: AccountKind;
  trackingMode: TrackingMode;
  currency: string;
  timeZone: string;
  policy: NegativeBalancePolicy;
  authorizedLimit: string;
  openingAmount: string;
  openingEffectiveAt: string;
}

export interface UpdateAccountMetadataFormValues {
  name: string;
  timeZone: string;
}

export interface UpdateAccountPolicyFormValues {
  policy: NegativeBalancePolicy;
  authorizedLimit: string;
}

export type OpeningCorrectionRequest = components['schemas']['OpeningCorrectionRequest'];

export interface OpeningCorrectionFormValues {
  amount: string;
  correctionReason: string;
}
