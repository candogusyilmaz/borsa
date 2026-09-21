import type { components } from '@/api/schema';

export type FinancialAccount = components['schemas']['FinancialAccountResponse'];
export type BalanceResponse = components['schemas']['BalanceResponse'];
export type AccountKind = FinancialAccount['kind'];
export type TrackingMode = FinancialAccount['trackingMode'];
export type NegativeBalancePolicy = NonNullable<FinancialAccount['policy']>;
export type CashCoverageStatus = FinancialAccount['cashCoverageStatus'];
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

export type ActivityResponse = components['schemas']['ActivityResponse'];
export type PostingResponse = components['schemas']['PostingResponse'];
export type SecurityPostingResponse = components['schemas']['SecurityPostingResponse'];
export type CashActivityRequest = components['schemas']['CashActivityRequest'];
export type ReversalRequest = components['schemas']['ReversalRequest'];
export type SliceResponseActivityResponse = components['schemas']['SliceResponseActivityResponse'];
export type ActivityType = ActivityResponse['activityType'];
export type PostingRole = PostingResponse['role'];
export type SecurityPostingRole = SecurityPostingResponse['role'];
export type RecordingMode = ActivityResponse['recordingMode'];
export type PolicyDecision = ActivityResponse['policyDecision'];
export type ManualCashActivityType = Extract<ActivityType, 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'CASH_FEE' | 'CASH_INTEREST_CREDIT'>;

export interface RecordCashActivityFormValues {
  activityType: ManualCashActivityType;
  amount: string;
  recordingMode: RecordingMode;
  effectiveAt: string;
  confirmPolicyBreach: boolean;
}

export interface ReverseActivityFormValues {
  correctionReason: string;
}

export type TransferPreviewRequest = components['schemas']['TransferPreviewRequest'];
export type TransferPreviewResponse = components['schemas']['TransferPreviewResponse'];
export type TransferRequest = components['schemas']['TransferRequest'];

export interface TransferFormValues {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  recordingMode: RecordingMode;
  effectiveAt: string;
  confirmPolicyBreach: boolean;
}
