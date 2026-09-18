import type { QueryClient } from '@tanstack/react-query';
import { type ApiError, showApiError } from '@/api/errors';

export function notifyPreviewError(err: unknown): ApiError {
  return showApiError(err, {
    title: 'Transfer Preview Failed',
    fallbackMessage: 'Could not preview transfer. Please verify your inputs.'
  });
}

export function notifyCommitError(err: unknown): ApiError {
  return showApiError(err, {
    title: 'Transfer Failed',
    fallbackMessage: 'Could not complete transfer.'
  });
}

export function invalidateTransferRelatedQueries(queryClient: QueryClient): void {
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] });
  queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] });
}
