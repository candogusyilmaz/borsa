import { getLocalePreferences } from '~/shared/i18n/i18n';
import { type AuthSession, isTokenExpiringSoon, sessionStore } from './token';

const LOCK_NAME = 'canverse-auth-refresh';
const REFRESH_TIMEOUT_MS = 10_000;

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';

let refreshPromise: Promise<string> | null = null;

async function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request(LOCK_NAME, task);
  }
  return task();
}

export async function getValidAccessToken(): Promise<string | null> {
  const session = sessionStore.getSession();
  if (!session) return null;

  if (!isTokenExpiringSoon(session.token)) {
    return session.token;
  }

  return refreshAccessToken(session.token);
}

export async function refreshAccessToken(failedToken: string | null): Promise<string> {
  const currentToken = sessionStore.getToken();

  if (currentToken && currentToken !== failedToken && !isTokenExpiringSoon(currentToken)) {
    return currentToken;
  }

  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = withCrossTabLock(async () => {
    const latestSession = sessionStore.getSession();
    if (!latestSession) {
      throw new Error('Session cleared');
    }

    if (latestSession.token !== failedToken && !isTokenExpiringSoon(latestSession.token)) {
      return latestSession.token;
    }

    const res = await fetch(`${apiBaseUrl}/api/auth/refresh-token`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept-Language': getLocalePreferences().language
      },
      signal: AbortSignal.timeout(REFRESH_TIMEOUT_MS)
    });

    if (res.status === 401 || res.status === 403) {
      sessionStore.clearSession();
      throw new Error('Refresh token expired or invalid');
    }

    if (!res.ok) {
      throw new Error(`Token refresh failed with status ${res.status}`);
    }

    const data = (await res.json()) as AuthSession;
    if (!data?.token) {
      sessionStore.clearSession();
      throw new Error('Invalid refresh response');
    }

    sessionStore.setSession(data);
    return data.token;
  }).finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}
