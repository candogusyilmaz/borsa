export type AppErrorKind = 'network' | 'client' | 'server' | 'unexpected';

export function classifyError(error: unknown, status?: number): AppErrorKind {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'network';
  }

  if (status !== undefined) {
    if (status >= 500) return 'server';
    if (status >= 400) return 'client';
  }

  if (error instanceof TypeError && /failed to fetch|networkerror|load failed|fetch failed/i.test(error.message)) {
    return 'network';
  }

  if (error instanceof Error && error.name === 'NetworkError') {
    return 'network';
  }

  return 'unexpected';
}
