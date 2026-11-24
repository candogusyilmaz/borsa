export type AppErrorKind = 'network' | 'server' | 'unexpected';

interface StatusLikeError {
  status?: number;
  statusCode?: number;
  response?: { status?: number };
}

export function getErrorStatus(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined;
  const candidate = error as StatusLikeError;
  return candidate.status ?? candidate.statusCode ?? candidate.response?.status;
}

export function classifyError(error: unknown): AppErrorKind {
  if (typeof navigator !== 'undefined' && !navigator.onLine) {
    return 'network';
  }

  const status = getErrorStatus(error);
  if (status !== undefined && status >= 500) {
    return 'server';
  }

  if (
    error instanceof TypeError &&
    /failed to fetch|networkerror|load failed|fetch failed/i.test(error.message)
  ) {
    return 'network';
  }

  if (error instanceof Error && error.name === 'NetworkError') {
    return 'network';
  }

  return 'unexpected';
}
