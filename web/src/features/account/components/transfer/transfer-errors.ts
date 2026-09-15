import { notifications } from '@mantine/notifications';
import type { QueryClient } from '@tanstack/react-query';
import { normalizeError } from '@/api/errors';

export interface TransferErrorPresentation {
  title: string;
  message: string;
  color?: string;
}

export const PREVIEW_ERROR_MESSAGES: Record<string, TransferErrorPresentation> = {
  FUTURE_TIME_NOT_ALLOWED: {
    title: 'Future Date Prohibited',
    message: 'Effective transfer date cannot be in the future.'
  },
  INSUFFICIENT_FUNDS: {
    title: 'Insufficient Funds',
    message: 'The source account does not have sufficient funds for this transfer.'
  },
  ACCOUNT_LIMIT_EXCEEDED: {
    title: 'Limit Exceeded',
    message: 'The transfer amount exceeds the authorized overdraft limit on the source account.'
  },
  ACCOUNT_CURRENCY_UNSUPPORTED: {
    title: 'Currency Mismatch',
    message: 'Transfers must be conducted between accounts sharing the exact same currency.'
  },
  ACCOUNT_ACTION_NOT_SUPPORTED: {
    title: 'Transfer Not Supported',
    message: 'One of the selected accounts cannot participate in ledger transfers.'
  }
};

export const COMMIT_ERROR_MESSAGES: Record<string, TransferErrorPresentation> = {
  INSUFFICIENT_FUNDS: {
    title: 'Insufficient Funds',
    message: 'The source account does not have sufficient funds.'
  },
  POLICY_BREACH_CONFIRMATION_REQUIRED: {
    title: 'Overdraft Confirmation Required',
    message: 'This transfer requires overdraft confirmation to proceed.',
    color: 'orange'
  },
  IDEMPOTENCY_CONFLICT: {
    title: 'Request Reference Conflict',
    message: 'Idempotency conflict encountered. Please try submitting again.',
    color: 'orange'
  }
};

export function notifyPreviewError(err: unknown): void {
  const apiErr = normalizeError(err);
  const code = apiErr.code || '';
  const presentation = (code ? PREVIEW_ERROR_MESSAGES[code] : undefined) ?? {
    title: 'Transfer Preview Failed',
    message: apiErr.message || 'Could not preview transfer. Please verify your inputs.'
  };

  notifications.show({
    title: presentation.title,
    message: presentation.message,
    color: presentation.color || 'red'
  });
}

export function notifyCommitError(err: unknown): void {
  const apiErr = normalizeError(err);

  if (apiErr.status === 409 || apiErr.code === 'BALANCE_VERSION_CONFLICT') {
    notifications.show({
      title: 'Balance Conflict (HTTP 409)',
      message: 'An account balance was updated by another transaction. Please reload balances and preview again.',
      color: 'orange'
    });
    return;
  }

  const code = apiErr.code || '';
  const presentation = (code ? COMMIT_ERROR_MESSAGES[code] : undefined) ?? {
    title: 'Transfer Failed',
    message: apiErr.message || 'Could not complete transfer.'
  };

  notifications.show({
    title: presentation.title,
    message: presentation.message,
    color: presentation.color || 'red'
  });
}

export function invalidateTransferRelatedQueries(queryClient: QueryClient): void {
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] });
}
