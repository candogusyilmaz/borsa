import type { QueryClient } from '@tanstack/react-query';
import type { User } from '@/shared/types/auth';
import { $api, clearAccessToken, getAccessToken, requestTokenRefresh } from './client';

export type SessionResolution =
  | {
      status: 'authenticated';
      user: User;
    }
  | {
      status: 'anonymous';
    };

/** Cache key of the authoritative authenticated-user query (/me). */
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

/**
 * True when an error means the current access token/session was rejected
 * (HTTP 401). The generated client throws the parsed RFC 7807 ProblemDetail
 * body for error responses, and ProblemDetail carries a numeric `status`.
 */
function isAuthenticationError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { status?: unknown }).status === 401;
}

/**
 * Resolve the current session. The HTTP client in this repository does not
 * auto-retry authenticated requests after a 401, so recovery is explicit here:
 *
 * - Access token present:
 *   - /me succeeds -> authenticated.
 *   - abort -> propagated (no state transition).
 *   - /me fails with 401 (token rejected) -> single refresh-cookie
 *     restoration below.
 *   - /me fails operationally (5xx/network/...) -> error propagated; the
 *     session is NOT destroyed for a non-auth failure.
 * - No access token: the refresh cookie is the only way to start a session.
 * - Restoration: one requestTokenRefresh() call (single shared mutex).
 *   - Refresh cannot restore -> local session cleared -> anonymous.
 *   - Refresh succeeds but /me fails with 401 -> local session cleared
 *     -> anonymous (no valid-looking token left behind).
 *   - Refresh succeeds but /me fails operationally -> error propagated;
 *     the freshly issued token is preserved.
 *
 * Refresh calls converge on the single requestTokenRefresh() mutex in the API
 * client; the resolver never opens a second refresh request.
 */
export async function resolveSession(queryClient: QueryClient): Promise<SessionResolution> {
  const hasToken = getAccessToken() !== null;

  if (hasToken) {
    try {
      const user = await fetchCurrentUser(queryClient);
      return { status: 'authenticated', user };
    } catch (error) {
      if (isAbortError(error)) {
        throw error;
      }
      if (!isAuthenticationError(error)) {
        throw error;
      }
      // The stored access token was rejected (401); restore through the
      // refresh cookie below.
    }
  }

  const restored = await requestTokenRefresh();
  if (!restored) {
    clearLocalSession(queryClient);
    return { status: 'anonymous' };
  }

  try {
    const user = await fetchCurrentUser(queryClient);
    return { status: 'authenticated', user };
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    if (isAuthenticationError(error)) {
      clearLocalSession(queryClient);
      return { status: 'anonymous' };
    }
    throw error;
  }
}
