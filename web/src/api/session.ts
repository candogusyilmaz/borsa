import type { QueryClient } from '@tanstack/react-query';

import type { User } from '@/shared/types/auth';
import { requestTokenRefresh } from './auth-recovery';
import { advanceSessionEpoch, clearAccessToken, currentSessionEpoch, getAccessToken } from './auth-state';
import { $api, client } from './client';

export type SessionResolution =
  | {
      status: 'authenticated';
      user: User;
    }
  | {
      status: 'anonymous';
    };

export const ME_QUERY_KEY = ['get', '/api/v1/me'] as const;

export function isAbortError(error: unknown): boolean {
  if (error instanceof Error && error.name === 'AbortError') {
    return true;
  }

  if (typeof error === 'object' && error !== null) {
    const candidate = error as {
      name?: unknown;
      code?: unknown;
    };

    return candidate.name === 'AbortError' || candidate.code === 20;
  }

  return false;
}

export async function fetchCurrentUser(queryClient: QueryClient): Promise<User> {
  const user = await queryClient.query($api.queryOptions('get', '/api/v1/me'));

  if (!user) {
    throw new Error('Failed to retrieve user profile');
  }

  return user;
}

export function clearLocalSession(queryClient: QueryClient): void {
  clearAccessToken();
  queryClient.clear();
}

function isAuthenticationError(error: unknown): boolean {
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

          return {
            status: 'anonymous'
          };
        }

        return {
          status: 'authenticated',
          user
        };
      } catch (error) {
        if (isAbortError(error)) {
          /*
           * A request superseded by another logical session should
           * resolve again against the new state rather than surface
           * as an application failure.
           */
          if (currentSessionEpoch() !== epochAtStart) {
            if (getAccessToken() !== null) {
              continue;
            }

            clearLocalSession(queryClient);

            return {
              status: 'anonymous'
            };
          }

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

          return {
            status: 'anonymous'
          };
        }

        // Transport already exhausted refresh recovery.
        clearLocalSession(queryClient);

        return {
          status: 'anonymous'
        };
      }
    }

    /*
     * There is no access token, so no authenticated request can
     * trigger transport recovery. Bootstrap explicitly through the
     * HttpOnly refresh cookie.
     */
    const restored = await requestTokenRefresh(epochAtStart);

    if (!restored) {
      if (currentSessionEpoch() !== epochAtStart) {
        if (getAccessToken() !== null) {
          continue;
        }

        clearLocalSession(queryClient);

        return {
          status: 'anonymous'
        };
      }

      clearLocalSession(queryClient);

      return {
        status: 'anonymous'
      };
    }

    if (currentSessionEpoch() !== epochAtStart) {
      if (getAccessToken() !== null) {
        continue;
      }

      clearLocalSession(queryClient);

      return {
        status: 'anonymous'
      };
    }

    try {
      const user = await fetchCurrentUser(queryClient);

      if (currentSessionEpoch() !== epochAtStart) {
        if (getAccessToken() !== null) {
          continue;
        }

        clearLocalSession(queryClient);

        return {
          status: 'anonymous'
        };
      }

      return {
        status: 'authenticated',
        user
      };
    } catch (error) {
      if (isAbortError(error)) {
        if (currentSessionEpoch() !== epochAtStart) {
          if (getAccessToken() !== null) {
            continue;
          }

          clearLocalSession(queryClient);

          return {
            status: 'anonymous'
          };
        }

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

        return {
          status: 'anonymous'
        };
      }

      clearLocalSession(queryClient);

      return {
        status: 'anonymous'
      };
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
    // Server logout is best-effort.
  }

  const currentEpoch = currentSessionEpoch();

  const hasToken = getAccessToken() !== null;

  /*
   * A different session was established while this logout was
   * running. The old logout must not destroy it.
   */
  if (currentEpoch !== epochAtStart && hasToken) {
    return;
  }

  /*
   * Terminal logout recovery may already have invalidated the
   * logical session.
   */
  if (currentEpoch === epochAtStart) {
    advanceSessionEpoch();
  }

  clearLocalSession(queryClient);
}
