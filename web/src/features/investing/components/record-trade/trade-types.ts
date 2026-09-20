import type { RecordingMode, TradePreviewResponse, TradeResponse, TradeSide } from '../../types';

export type TradeStep = 'edit' | 'preview' | 'success';

export interface TradeSessionData {
  preview: TradePreviewResponse;
  requestBody: {
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
  };
  clientRequestId: string;
}

export type TradeState =
  | { step: 'edit' }
  | {
      step: 'preview';
      session: TradeSessionData;
    }
  | {
      step: 'success';
      trade: TradeResponse;
      preview: TradePreviewResponse;
    };

export interface RecordTradeProps {
  defaultAccountId?: string;
  defaultInstrumentId?: string;
  defaultSide?: TradeSide;
  lockAccount?: boolean;
  lockInstrument?: boolean;
}
