import { currentSessionEpoch, getAccessToken, invalidateSession, setAccessToken } from './auth-state';
import { API_BASE_URL } from './config';

export type RecoveryPolicy = 'bypass' | 'route-owned' | 'command-owned' | 'global';

const BYPASS_PATHS = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/register']);

export function getRecoveryPolicy(pathOrPathname: string): RecoveryPolicy {
  if (BYPASS_PATHS.has(pathOrPathname)) {
    return 'bypass';
  }

  if (pathOrPathname === '/api/v1/me') {
    return 'route-owned';
  }

  if (pathOrPathname === '/api/v1/auth/logout') {
    return 'command-owned';
  }

  return 'global';
}

interface ActiveRefresh {
  epoch: number;
  baseUrl: string;
  promise: Promise<string | null>;
}

let activeRefresh: ActiveRefresh | null = null;

function refreshUrl(baseUrl: string): string {
  return `${baseUrl.replace(/\/$/, '')}/api/v1/auth/refresh`;
}

/**
 * Refreshes the access token for a particular logical session.
 *
 * Returns:
 * - token: successful same-session rotation
 * - null: definitive 401 rejection or operation superseded by another session
 *
 * Throws:
 * - network failure
 * - 5xx / unexpected HTTP failure
 * - malformed successful refresh response
 */
export async function requestTokenRefresh(
  expectedEpoch: number = currentSessionEpoch(),
  baseUrl: string = API_BASE_URL
): Promise<string | null> {
  if (expectedEpoch !== currentSessionEpoch()) {
    return null;
  }

  if (activeRefresh?.epoch === expectedEpoch && activeRefresh.baseUrl === baseUrl) {
    return activeRefresh.promise;
  }

  const refreshEpoch = expectedEpoch;

  const promise = performRefresh(baseUrl, refreshEpoch).finally(() => {
    if (activeRefresh?.epoch === refreshEpoch && activeRefresh.baseUrl === baseUrl) {
      activeRefresh = null;
    }
  });

  activeRefresh = {
    epoch: refreshEpoch,
    baseUrl,
    promise
  };

  return promise;
}

async function performRefresh(baseUrl: string, refreshEpoch: number): Promise<string | null> {
  let response: Response;

  try {
    response = await fetch(refreshUrl(baseUrl), {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        refreshTokenDelivery: 'HTTP_ONLY_COOKIE'
      })
    });
  } catch (error) {
    // An old refresh must not surface errors into a newer logical session.
    if (currentSessionEpoch() !== refreshEpoch) {
      return null;
    }

    throw error;
  }

  // Ignore every outcome belonging to an old logical session.
  if (currentSessionEpoch() !== refreshEpoch) {
    return null;
  }

  // Backend contract: 401 definitively rejects the refresh session.
  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw Object.assign(new Error(`Token refresh failed: HTTP ${response.status}`), {
      status: response.status
    });
  }

  const data = (await response.json()) as {
    accessToken?: string;
  } | null;

  if (!data?.accessToken) {
    throw new Error('Token refresh succeeded without returning an access token');
  }

  // JSON parsing itself took time, so check again before mutating state.
  if (currentSessionEpoch() !== refreshEpoch) {
    return null;
  }

  setAccessToken(data.accessToken);

  return data.accessToken;
}

function supersededSessionResponse() {
  return new Response(
    JSON.stringify({
      status: 401,
      title: 'Request superseded',
      detail: 'The authenticated session changed while the request was in flight.'
    }),
    {
      status: 401,
      headers: { 'Content-Type': 'application/problem+json' }
    }
  );
}

async function retryWithToken(request: Request, token: string): Promise<Response> {
  const headers = new Headers(request.headers);

  headers.set('Authorization', `Bearer ${token}`);

  return fetch(
    new Request(request, {
      headers
    })
  );
}

export function createRecoveringFetch(baseUrl: string = API_BASE_URL) {
  return async function recoveringFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const request = input instanceof Request ? input : new Request(input, init);

    const pathname = new URL(request.url).pathname;
    const policy = getRecoveryPolicy(pathname);

    // Login/refresh/register are ordinary requests and never participate
    // in access-token recovery.
    if (policy === 'bypass') {
      return fetch(input, init);
    }

    const epochAtDispatch = currentSessionEpoch();

    const tokenAtDispatch = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '') ?? null;

    // Clone before the first fetch consumes a potential request body.
    const replayableRequest = request.clone();

    const firstResponse = await fetch(input, init);

    // A successful or operational response dispatched by an earlier session
    // must never reach the current session's cache or mutation callbacks.
    if (epochAtDispatch !== currentSessionEpoch()) {
      return firstResponse.status === 401 ? firstResponse : supersededSessionResponse();
    }

    if (firstResponse.status !== 401) {
      return firstResponse;
    }

    const currentToken = getAccessToken();

    /*
     * Another request may already have refreshed while this request
     * was in flight.
     *
     * Same logical epoch + different token = retry using current token,
     * without another refresh.
     */
    if (currentToken !== null && currentToken !== tokenAtDispatch) {
      if (currentSessionEpoch() !== epochAtDispatch || getAccessToken() !== currentToken) {
        return firstResponse;
      }

      const retryResponse = await retryWithToken(replayableRequest, currentToken);

      if (currentSessionEpoch() !== epochAtDispatch) {
        return firstResponse;
      }

      if (retryResponse.status === 401) {
        invalidateSession({
          expectedEpoch: epochAtDispatch,
          expectedToken: currentToken,
          notify: policy === 'global'
        });
      }

      return retryResponse;
    }

    const refreshedToken = await requestTokenRefresh(epochAtDispatch, baseUrl);

    if (!refreshedToken) {
      invalidateSession({
        expectedEpoch: epochAtDispatch,
        expectedToken: tokenAtDispatch,
        notify: policy === 'global'
      });

      return firstResponse;
    }

    if (currentSessionEpoch() !== epochAtDispatch || getAccessToken() !== refreshedToken) {
      return firstResponse;
    }

    const retryResponse = await retryWithToken(replayableRequest, refreshedToken);

    if (currentSessionEpoch() !== epochAtDispatch) {
      return firstResponse;
    }

    if (retryResponse.status === 401) {
      invalidateSession({
        expectedEpoch: epochAtDispatch,
        expectedToken: refreshedToken,
        notify: policy === 'global'
      });
    }

    return retryResponse;
  };
}
