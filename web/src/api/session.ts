import type { QueryClient } from '@tanstack/react-query';
import type { User } from '@/shared/types/auth';
import { $api, advanceSessionEpoch, clearAccessToken, client, currentSessionEpoch, getAccessToken, requestTokenRefresh } from './client';

export type SessionResolution =
  | {
      status: 'authenticated';
      user: User;
    }
  | {
      status: 'anonymous';
    };

export const ME_QUERY_KEY = ['get', '/api/v1/me'] as const;

export function isAbortError(error: unknown) {
  if (error instanceof Error && error.name === 'AbortError') {
    return true;
  }
  if (typeof error === 'object' && error !== null) {
    const candidate = error as { name?: unknown; code?: unknown };
    return candidate.name === 'AbortError' || candidate.code === 20;
  }
  return false;
}

export async function fetchCurrentUser(queryClient: QueryClient) {
  const user = await queryClient.query($api.queryOptions('get', '/api/v1/me'));
  if (!user) {
    throw new Error('Failed to retrieve user profile');
  }
  return user;
}

export function clearLocalSession(queryClient: QueryClient) {
  clearAccessToken();
  queryClient.clear();
}

function isAuthenticationError(error: unknown) {
  return typeof error === 'object' && error !== null && (error as { status?: unknown }).status === 401;
}

export async function resolveSession(queryClient: QueryClient): Promise<SessionResolution> {
  while (true) {
    const epochAtStart = currentSessionEpoch();
    const hasToken = getAccessToken() !== null;

    if (hasToken) {
      try {
        const user = await fetchCurrentUser(queryClient);
        if (currentSessionEpoch() !== epochAtStart) {
          if (getAccessToken() !== null) {
            continue;
          }
          clearLocalSession(queryClient);
          return { status: 'anonymous' };
        }
        return { status: 'authenticated', user };
      } catch (error) {
        if (isAbortError(error)) {
          throw error;
        }
        if (!isAuthenticationError(error)) {
          throw error;
        }
        if (currentSessionEpoch() !== epochAtStart) {
          if (getAccessToken() !== null) {
            // New credentials exist; re-resolve against current session.
            continue;
          }
          // Session was terminally invalidated or logged out (credentials gone).
          clearLocalSession(queryClient);
          return { status: 'anonymous' };
        }
        // Transport already attempted one refresh; do not try again.
        clearLocalSession(queryClient);
        return { status: 'anonymous' };
      }
    }

    // No token: explicit bootstrap refresh (no Bearer request possible yet).
    const restored = await requestTokenRefresh(epochAtStart);
    if (!restored) {
      if (currentSessionEpoch() !== epochAtStart) {
        if (getAccessToken() !== null) {
          continue;
        }
        clearLocalSession(queryClient);
        return { status: 'anonymous' };
      }
      // Definitive refresh rejection in SAME epoch: clear dead local session.
      clearLocalSession(queryClient);
      return { status: 'anonymous' };
    }

    if (currentSessionEpoch() !== epochAtStart) {
      if (getAccessToken() !== null) {
        continue;
      }
      clearLocalSession(queryClient);
      return { status: 'anonymous' };
    }

    try {
      const user = await fetchCurrentUser(queryClient);
      if (currentSessionEpoch() !== epochAtStart) {
        if (getAccessToken() !== null) {
          continue;
        }
        clearLocalSession(queryClient);
        return { status: 'anonymous' };
      }
      return { status: 'authenticated', user };
    } catch (error) {
      if (isAbortError(error)) {
        throw error;
      }
      if (!isAuthenticationError(error)) {
        throw error;
      }
      if (currentSessionEpoch() !== epochAtStart) {
        if (getAccessToken() !== null) {
          continue;
        }
        clearLocalSession(queryClient);
        return { status: 'anonymous' };
      }
      clearLocalSession(queryClient);
      return { status: 'anonymous' };
    }
  }
}

export async function logoutSession(queryClient: QueryClient): Promise<void> {
  const epochAtStart = currentSessionEpoch();
  try {
    if (getAccessToken()) {
      await client.POST('/api/v1/auth/logout', {
        body: {
          scope: 'CURRENT_SESSION'
        }
      });
    }
  } catch {
    // Ignore network errors during logout
  }

  const currentEpoch = currentSessionEpoch();
  const hasToken = getAccessToken() !== null;

  // If a new session was established with new credentials while logout was in flight,
  // do not clear the new session/token/cache.
  if (currentEpoch !== epochAtStart && hasToken) {
    return;
  }

  // If transport already advanced the epoch (e.g. terminal invalidation during /logout),
  // do not advance the epoch a second time.
  if (currentEpoch === epochAtStart) {
    advanceSessionEpoch();
  }

  clearLocalSession(queryClient);
}
