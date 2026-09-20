import type { components } from '@/api/schema';

export type TradePreviewRequest = components['schemas']['TradePreviewRequest'];
export type TradePreviewResponse = components['schemas']['TradePreviewResponse'];
export type TradeCommitRequest = components['schemas']['TradeCommitRequest'];
export type TradeResponse = components['schemas']['TradeResponse'];
export type TradeSummaryResponse = components['schemas']['TradeSummaryResponse'];
export type PositionResponse = components['schemas']['PositionResponse'];
export type SecurityPostingResponse = components['schemas']['SecurityPostingResponse'];

export type TradeSide = 'BUY' | 'SELL';
export type CalculationPolicy = 'WEIGHTED_AVERAGE_ECONOMIC_V1';
export type ProjectionStatus = PositionResponse['projectionStatus'];
export type PolicyDecision = TradePreviewResponse['policyDecision'];
export type RecordingMode = TradePreviewResponse['recordingMode'];
export type InstrumentType = PositionResponse['instrumentType'];

export type SliceResponseTradeSummaryResponse = components['schemas']['SliceResponseTradeSummaryResponse'];
export type SliceResponsePositionResponse = components['schemas']['SliceResponsePositionResponse'];

export interface TradeFormValues {
  accountId: string;
  instrumentId: string;
  side: TradeSide;
  quantity: string;
  unitPrice: string;
  commissionAmount: string;
  recordingMode: RecordingMode;
  effectiveAt: string;
  economicSequence: number;
  confirmPolicyBreach: boolean;
}
