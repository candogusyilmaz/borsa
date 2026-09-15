import type { ActivityResponse, FinancialAccount, RecordingMode, TransferPreviewResponse } from '../../types';

export type { TransferPreviewResponse };

export interface TransferFormValues {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  recordingMode: RecordingMode;
  effectiveAt: string;
  confirmPolicyBreach: boolean;
}

export interface TransferPreviewSession {
  preview: TransferPreviewResponse;
  requestBody: {
    sourceAccountId: string;
    destinationAccountId: string;
    amount: string;
    recordingMode: RecordingMode;
    effectiveAt: string;
    confirmPolicyBreach: boolean;
  };
  clientRequestId: string;
}

export type TransferStep = 'edit' | 'preview' | 'success';

export type TransferState =
  | { step: 'edit' }
  | {
      step: 'preview';
      session: TransferPreviewSession;
    }
  | {
      step: 'success';
      activity: ActivityResponse;
      preview: TransferPreviewResponse;
    };

export interface TransferPolicyPresentation {
  severity: 'info' | 'warning' | 'error';
  title: string;
  description: string;
  requiresConfirmation?: boolean;
}

export interface TransferDefaultsOptions {
  accounts: FinancialAccount[];
  defaultSourceAccountId?: string;
  defaultDestinationAccountId?: string;
  lockSourceAccount?: boolean;
}
