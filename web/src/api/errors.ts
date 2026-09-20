import { notifications } from '@mantine/notifications';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { createElement } from 'react';

export interface ApiFieldError {
  field: string;
  key?: string;
  detail: string;
}

export interface ApiError {
  status: number;
  message: string;
  title?: string;
  detail?: string;
  code?: string;
  key?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
}

export interface ShowApiErrorOptions {
  title?: string;
  fallbackMessage?: string;
}

const IS_NORMALIZED_API_ERROR = Symbol.for('stocks.isNormalizedApiError');

export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === 'object' &&
    error !== null &&
    (IS_NORMALIZED_API_ERROR in error ||
      (typeof (error as ApiError).status === 'number' && typeof (error as ApiError).message === 'string' && !('params' in error)))
  );
}

function markNormalized(apiError: ApiError) {
  Object.defineProperty(apiError, IS_NORMALIZED_API_ERROR, {
    value: true,
    enumerable: false,
    configurable: true
  });
  return apiError;
}

function parseStructuredError(candidate: Record<string, unknown>, rawMessageFallback?: string) {
  const status =
    typeof candidate.status === 'number'
      ? candidate.status
      : typeof candidate.status === 'string' && !Number.isNaN(Number(candidate.status))
        ? Number(candidate.status)
        : 500;

  const candidateParams = candidate.params as Record<string, unknown> | undefined;

  const detail =
    typeof candidate.detail === 'string' && candidate.detail.trim().length > 0
      ? candidate.detail
      : typeof candidateParams?.detail === 'string' && candidateParams.detail.trim().length > 0
        ? (candidateParams.detail as string)
        : undefined;

  const title = typeof candidate.title === 'string' && candidate.title.trim().length > 0 ? candidate.title : undefined;

  const rawMessage =
    typeof candidate.message === 'string' && candidate.message.trim().length > 0
      ? candidate.message
      : rawMessageFallback && rawMessageFallback.trim().length > 0
        ? rawMessageFallback
        : undefined;

  const code = typeof candidate.code === 'string' && candidate.code.trim().length > 0 ? candidate.code : undefined;

  const key = typeof candidate.key === 'string' && candidate.key.trim().length > 0 ? candidate.key : undefined;

  const traceId = typeof candidate.traceId === 'string' && candidate.traceId.trim().length > 0 ? candidate.traceId : undefined;

  let fieldErrors: ApiFieldError[] | undefined;

  if (Array.isArray(candidate.fieldErrors) && candidate.fieldErrors.length > 0) {
    const parsed = candidate.fieldErrors
      .filter((entry): entry is Record<string, unknown> => typeof entry === 'object' && entry !== null)
      .map((entry) => ({
        field: typeof entry.field === 'string' ? entry.field : '',
        key: typeof entry.key === 'string' ? entry.key : undefined,
        detail: typeof entry.detail === 'string' ? entry.detail : 'Validation failed'
      }));
    if (parsed.length > 0) {
      fieldErrors = parsed;
    }
  }

  const paramsErrors = candidateParams?.errors;
  if (!fieldErrors && Array.isArray(paramsErrors) && paramsErrors.length > 0) {
    const parsed = paramsErrors
      .filter((entry): entry is Record<string, unknown> => typeof entry === 'object' && entry !== null && typeof entry.detail === 'string')
      .map((entry) => ({
        field: typeof entry.field === 'string' ? entry.field : '',
        key: typeof entry.key === 'string' ? entry.key : undefined,
        detail: typeof entry.detail === 'string' ? entry.detail : 'Validation failed'
      }));
    if (parsed.length > 0) {
      fieldErrors = parsed;
    }
  }

  const fieldErrorsSummary = fieldErrors && fieldErrors.length > 0 ? fieldErrors.map((f) => f.detail).join('; ') : undefined;

  const message = detail ?? fieldErrorsSummary ?? title ?? rawMessage ?? 'An unexpected error occurred';

  return markNormalized({
    status,
    message,
    title,
    detail,
    code,
    key,
    traceId,
    fieldErrors
  });
}

export function normalizeError(error: unknown) {
  if (isApiError(error)) {
    return error;
  }

  if (error instanceof Error) {
    return parseStructuredError(error as unknown as Record<string, unknown>, error.message);
  }

  if (typeof error !== 'object' || error === null) {
    return markNormalized({
      status: 500,
      message: typeof error === 'string' && error.trim().length > 0 ? error : 'An unexpected error occurred'
    });
  }

  return parseStructuredError(error as Record<string, unknown>);
}

export function getApiErrorMessage(error: ApiError, fallbackMessage?: string) {
  if (error.fieldErrors && error.fieldErrors.length > 0) {
    return error.fieldErrors.map((f) => f.detail).join('; ');
  }
  if (error.detail) {
    return error.detail;
  }
  if (fallbackMessage) {
    return fallbackMessage;
  }
  if (error.message && error.message !== 'An unexpected error occurred') {
    return error.message;
  }
  if (error.title) {
    return error.title;
  }
  return error.message;
}

export function showApiError(error: unknown, options?: ShowApiErrorOptions) {
  const apiError = isApiError(error) ? error : normalizeError(error);
  const message = getApiErrorMessage(apiError, options?.fallbackMessage);

  notifications.show({
    title: options?.title ?? apiError.title ?? 'Error',
    message,
    color: 'red',
    icon: createElement(WarningCircleIcon, { size: 18, weight: 'bold' })
  });

  return apiError;
}
