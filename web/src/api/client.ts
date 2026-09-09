import createFetchClient, { type Middleware } from 'openapi-fetch';
import createClient from 'openapi-react-query';
import type { paths } from './schema';

export interface ApiFieldError {
  field: string;
  key?: string;
  detail: string;
}

export interface ApiError {
  status: number;
  message: string;
  code?: string;
  key?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
}

const configuredBaseUrl =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ||
  (typeof window !== 'undefined' && window.location?.origin ? window.location.origin : 'http://localhost');

const STORAGE_KEY = 'stocks_access_token';
let memoryAccessToken: string | null = null;

export function getAccessToken(): string | null {
  if (memoryAccessToken) return memoryAccessToken;
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored) {
      memoryAccessToken = stored;
      return stored;
    }
  } catch {
    // sessionStorage unavailable
  }
  return null;
}

export function setAccessToken(token: string | null) {
  memoryAccessToken = token;
  try {
    if (token) {
      sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // sessionStorage unavailable
  }
}

export function clearAccessToken() {
  setAccessToken(null);
}

// Logical session epoch -- advances on login, logout, and terminal session loss only.
// Access-token rotation (refresh within the same session) does NOT advance it.
let sessionEpoch = 0;

export function advanceSessionEpoch() {
  sessionEpoch++;
}

export function currentSessionEpoch() {
  return sessionEpoch;
}

let sessionLossHandler: (() => void) | null = null;

// Returns an unregister function; safe across HMR/StrictMode.
export function registerSessionLossHandler(handler: () => void) {
  sessionLossHandler = handler;
  return () => {
    if (sessionLossHandler === handler) {
      sessionLossHandler = null;
    }
  };
}

interface InvalidateSessionOptions {
  expectedEpoch: number;
  expectedToken: string | null;
  notify: boolean;
}

// Terminal session invalidation must bind to both expected epoch and expected token.
// If either differs from current state, the terminal result is stale (no-op).
// On valid terminal invalidation:
// clear access token -> advance logical session epoch exactly once -> optionally invoke global handler.
function invalidateSession(options: InvalidateSessionOptions) {
  if (options.expectedEpoch !== sessionEpoch) return;
  if (getAccessToken() !== options.expectedToken) return;

  clearAccessToken();
  advanceSessionEpoch();
  if (options.notify) sessionLossHandler?.();
}

// Recovery policy determines terminal-failure behavior for each path category.
// bypass        -- no recovery (login/refresh/register)
// route-owned   -- recovery + epoch invalidation; Router redirect owns navigation (/me)
// command-owned -- recovery + epoch invalidation; command caller owns navigation (/logout)
// global        -- recovery + epoch invalidation + global navigation signal (everything else)
type RecoveryPolicy = 'bypass' | 'route-owned' | 'command-owned' | 'global';

const BYPASS_PATHS = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/register']);

function getRecoveryPolicy(pathOrPathname: string): RecoveryPolicy {
  if (BYPASS_PATHS.has(pathOrPathname)) return 'bypass';
  if (pathOrPathname === '/api/v1/me') return 'route-owned';
  if (pathOrPathname === '/api/v1/auth/logout') return 'command-owned';
  return 'global';
}

// Epoch-scoped refresh mutex: at most one refresh per logical session in flight.
// Requests belonging to a different logical epoch must not join or be affected by
// a refresh started for an older epoch.
let activeRefresh: { epoch: number; promise: Promise<string | null> } | null = null;

// Uses native fetch directly to avoid routing through recoveringFetch.
// Returns the new access token on success.
// Returns null for definitive rejection (HTTP 401) or when the refresh is superseded
//   by a logical-session change that occurred while the refresh was in flight.
// THROWS for operational failures (5xx, network, etc.) so callers can propagate appropriately.
export async function requestTokenRefresh(expectedEpoch?: number): Promise<string | null> {
  const callerEpoch = expectedEpoch ?? sessionEpoch;

  if (expectedEpoch !== undefined && expectedEpoch !== sessionEpoch) {
    return null;
  }

  // Join an existing in-flight refresh for the same logical session.
  if (activeRefresh?.epoch === callerEpoch) return activeRefresh.promise;

  const refreshEpoch = callerEpoch;
  const promise = (async () => {
    try {
      let response: Response;
      try {
        response = await fetch(`${configuredBaseUrl}/api/v1/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshTokenDelivery: 'HTTP_ONLY_COOKIE' })
        });
      } catch (error) {
        // Discard network rejections if the logical session changed while in flight.
        if (sessionEpoch !== refreshEpoch) {
          return null;
        }
        throw error;
      }

      // Discard responses if the logical session changed while in flight.
      if (sessionEpoch !== refreshEpoch) {
        return null;
      }

      // Definitive rejection: the refresh session is invalid or revoked.
      // Backend evidence establishes only 401 Unauthorized for invalid/revoked refresh credentials.
      if (response.status === 401) return null;

      // Operational failure: throw so callers can surface it as an application error.
      if (!response.ok) {
        throw Object.assign(new Error(`Token refresh failed: HTTP ${response.status}`), {
          status: response.status
        });
      }

      const data = (await response.json()) as { accessToken?: string } | null;
      const token = data?.accessToken ?? null;
      if (!token) return null;

      if (sessionEpoch !== refreshEpoch) return null;

      setAccessToken(token);
      return token;
    } finally {
      if (activeRefresh?.epoch === refreshEpoch) activeRefresh = null;
    }
  })();

  activeRefresh = { epoch: refreshEpoch, promise };
  return promise;
}

async function retryWithToken(requestClone: Request, token: string) {
  const headers = new Headers(requestClone.headers);
  headers.set('Authorization', `Bearer ${token}`);
  return fetch(new Request(requestClone, { headers }));
}

async function recoveringFetch(input: RequestInfo | URL, init?: RequestInit) {
  const request = input instanceof Request ? input : new Request(input, init);
  const pathname = new URL(request.url).pathname;
  const policy = getRecoveryPolicy(pathname);

  if (policy === 'bypass') return fetch(input, init);

  // Capture epoch and token BEFORE the first network call.
  const epochAtDispatch = sessionEpoch;
  const tokenAtDispatch = request.headers.get('Authorization')?.replace('Bearer ', '') ?? null;
  const requestClone = request.clone();

  const firstResponse = await fetch(input, init);

  if (firstResponse.status !== 401) return firstResponse;

  // Cross-session protection: the logical session changed while this request was in flight.
  if (epochAtDispatch !== sessionEpoch) return firstResponse;

  const currentToken = getAccessToken();

  // Stale-401: another request already refreshed the token within the same session.
  if (currentToken !== null && currentToken !== tokenAtDispatch) {
    if (sessionEpoch !== epochAtDispatch || getAccessToken() !== currentToken) return firstResponse;
    const retryResponse = await retryWithToken(requestClone, currentToken);
    if (retryResponse.status === 401) {
      invalidateSession({
        expectedEpoch: epochAtDispatch,
        expectedToken: currentToken,
        notify: policy === 'global'
      });
    }
    return retryResponse;
  }

  // requestTokenRefresh throws on operational failure; let that propagate naturally.
  const newToken = await requestTokenRefresh(epochAtDispatch);
  if (!newToken) {
    // null = definitive rejection or superseded epoch; invalidateSession handles mismatch as no-op.
    invalidateSession({
      expectedEpoch: epochAtDispatch,
      expectedToken: tokenAtDispatch,
      notify: policy === 'global'
    });
    return firstResponse;
  }

  // Invariant: old logical-session request can never be replayed after its logical session epoch changes,
  // or if the newly refreshed token is no longer the active access token.
  if (sessionEpoch !== epochAtDispatch || getAccessToken() !== newToken) {
    return firstResponse;
  }

  const retryResponse = await retryWithToken(requestClone, newToken);
  if (retryResponse.status === 401) {
    invalidateSession({
      expectedEpoch: epochAtDispatch,
      expectedToken: newToken,
      notify: policy === 'global'
    });
  }
  return retryResponse;
}

const authMiddleware: Middleware = {
  // Attaches Bearer token before recoveringFetch snapshots tokenAtDispatch.
  async onRequest({ schemaPath, request }) {
    const pathname = new URL(request.url).pathname;
    const path = schemaPath ?? pathname;
    if (getRecoveryPolicy(path) === 'bypass' || getRecoveryPolicy(pathname) === 'bypass') return;
    const token = getAccessToken();
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  }
};

export function createApiClient(baseUrl: string) {
  const c = createFetchClient<paths>({
    baseUrl,
    credentials: 'include',
    fetch: recoveringFetch
  });
  c.use(authMiddleware);
  return c;
}

export const client = createApiClient(configuredBaseUrl);
export const $api = createClient(client);

export function normalizeError(error: unknown): ApiError {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as Record<string, unknown>;
    const status = typeof candidate.status === 'number' ? candidate.status : 500;
    const detail = typeof candidate.detail === 'string' ? candidate.detail : undefined;
    const title = typeof candidate.title === 'string' ? candidate.title : undefined;
    const message = detail || title || (typeof candidate.message === 'string' ? candidate.message : 'An error occurred');
    const code = typeof candidate.code === 'string' ? candidate.code : undefined;
    const key = typeof candidate.key === 'string' ? candidate.key : undefined;
    const traceId = typeof candidate.traceId === 'string' ? candidate.traceId : undefined;

    let fieldErrors: ApiFieldError[] | undefined;
    const params = candidate.params as { errors?: Array<{ field?: string; key?: string; detail?: string }> } | undefined;
    if (params?.errors && Array.isArray(params.errors)) {
      fieldErrors = params.errors
        .filter((e) => typeof e?.detail === 'string')
        .map((e) => ({ field: e.field || '', key: e.key, detail: e.detail || 'Validation failed' }));
    }
    return { status, message, code, key, traceId, fieldErrors };
  }
  if (error instanceof Error) return { status: 500, message: error.message };
  return { status: 500, message: String(error || 'An unexpected error occurred') };
}
