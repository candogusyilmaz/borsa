import { unwrapApiResponse } from './errors';
import { type AuthSession, sessionStore } from './token';

const LOCK_NAME = 'canverse-auth-refresh';
export const AUTH_REQUEST_TIMEOUT_MS = 10_000;

type RequestRefresh = (signal: AbortSignal) => Promise<{ data?: AuthSession; error?: unknown; response: Response }>;

let pendingRefresh: { email: string; promise: Promise<AuthSession | null> } | null = null;

export async function withAuthLock<T>(task: () => Promise<T>): Promise<T> {
  return navigator.locks ? navigator.locks.request(LOCK_NAME, task) : task();
}

export async function refreshAccessToken(attempt: AuthSession, requestRefresh: RequestRefresh): Promise<AuthSession | null> {
  const { email, token } = attempt;
  const current = sessionStore.getSession();
  if (current?.email !== email) return null;
  if (current.token !== token) return current;
  if (pendingRefresh?.email === email) return pendingRefresh.promise;

  const promise = withAuthLock(async () => {
    const before = sessionStore.getSession();
    if (before?.email !== email) return null;
    if (before.token !== token) return before;

    const result = await requestRefresh(AbortSignal.timeout(AUTH_REQUEST_TIMEOUT_MS));
    const after = sessionStore.getSession();
    if (after?.email !== email) return null;
    if (after.token !== token) return after;

    if (result.response.status === 401) {
      sessionStore.clearSession();
      return null;
    }

    const session = unwrapApiResponse(result);
    if (
      session?.email !== email ||
      typeof session.token !== 'string' ||
      !session.token ||
      typeof session.name !== 'string' ||
      !Array.isArray(session.permissions)
    ) {
      throw new Error('Invalid refresh response');
    }

    sessionStore.setSession(session);
    return session;
  }).finally(() => {
    if (pendingRefresh?.promise === promise) pendingRefresh = null;
  });

  pendingRefresh = { email, promise };
  return promise;
}
