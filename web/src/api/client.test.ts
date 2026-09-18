import { notifications } from '@mantine/notifications';
import { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRecoveringFetch, requestTokenRefresh } from './auth-recovery';
import {
  advanceSessionEpoch,
  clearAccessToken,
  currentSessionEpoch,
  getAccessToken,
  registerSessionLossHandler,
  setAccessToken
} from './auth-state';
import { createApiClient } from './client';
import { getApiErrorMessage, isApiError, normalizeError, showApiError } from './errors';
import { logoutSession, resolveSession } from './session';

// All tests use the exact same recoveringFetch as production via createApiClient.
let testClient: ReturnType<typeof createApiClient>;
let sessionLossCount: number;
let unregister: (() => void) | null = null;

beforeEach(() => {
  clearAccessToken();
  sessionLossCount = 0;
  unregister = registerSessionLossHandler(() => {
    sessionLossCount++;
  });
  testClient = createApiClient('http://localhost');
  vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
  unregister?.();
  unregister = null;
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

// Helpers
function json200(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}
function response401() {
  return new Response(JSON.stringify({ status: 401, title: 'Unauthorized' }), {
    status: 401,
    headers: { 'Content-Type': 'application/json' }
  });
}
function response500() {
  return new Response(JSON.stringify({ status: 500, title: 'Error' }), {
    status: 500,
    headers: { 'Content-Type': 'application/json' }
  });
}
function refreshSuccess(token: string) {
  return new Response(
    JSON.stringify({ sessionId: 's', accessToken: token, accessTokenExpiresAt: '', refreshTokenExpiresAt: '', serverTime: '' }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  );
}
function meUser() {
  return { id: 'u1', email: 'test@example.com', createdAt: '2024-01-01T00:00:00Z' };
}
function sessions200() {
  return json200([]);
}
// Generic background endpoint -- GET /api/v1/auth/sessions (not in bypass list, not /me, not /logout)
function sessions() {
  return testClient.GET('/api/v1/auth/sessions');
}

// 1. Valid token
describe('valid token', () => {
  it('200 response; no refresh; no session loss', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockResolvedValueOnce(json200(meUser()));

    const { data, error } = await testClient.GET('/api/v1/me');

    expect(error).toBeUndefined();
    expect(data).toMatchObject({ id: 'u1' });
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });
});

describe('cross-session response isolation', () => {
  it('rejects a successful response that completes after a new logical session starts', async () => {
    setAccessToken('TOKEN_A');

    let resolveOldRequest!: (response: Response) => void;
    const oldRequestGate = new Promise<Response>((resolve) => {
      resolveOldRequest = resolve;
    });

    vi.mocked(fetch).mockReturnValueOnce(oldRequestGate);

    const recoveringFetch = createRecoveringFetch('http://localhost');
    const oldRequest = recoveringFetch(
      new Request('http://localhost/api/v1/auth/sessions', {
        headers: { Authorization: 'Bearer TOKEN_A' }
      })
    );

    advanceSessionEpoch();
    clearAccessToken();
    setAccessToken('TOKEN_NEW');
    advanceSessionEpoch();
    resolveOldRequest(sessions200());

    await expect(oldRequest).resolves.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBe('TOKEN_NEW');
  });

  it('rejects a successful retry that completes after a new logical session starts', async () => {
    setAccessToken('TOKEN_A');

    let resolveOldRetry!: (response: Response) => void;
    const oldRetryGate = new Promise<Response>((resolve) => {
      resolveOldRetry = resolve;
    });

    let retryStartedResolve!: () => void;
    const retryStarted = new Promise<void>((resolve) => {
      retryStartedResolve = resolve;
    });

    vi.mocked(fetch).mockImplementation(async (request) => {
      const url = request instanceof Request ? request.url : String(request);
      const authorization = request instanceof Request ? request.headers.get('Authorization') : null;

      if (url.includes('/auth/refresh')) {
        return refreshSuccess('TOKEN_B');
      }
      if (authorization === 'Bearer TOKEN_B') {
        retryStartedResolve();
        return oldRetryGate;
      }
      return response401();
    });

    const recoveringFetch = createRecoveringFetch('http://localhost');
    const oldRequest = recoveringFetch(
      new Request('http://localhost/api/v1/auth/sessions', {
        headers: { Authorization: 'Bearer TOKEN_A' }
      })
    );

    await retryStarted;
    advanceSessionEpoch();
    clearAccessToken();
    setAccessToken('TOKEN_NEW');
    advanceSessionEpoch();
    resolveOldRetry(sessions200());

    await expect(oldRequest).resolves.toMatchObject({ status: 401 });
    expect(getAccessToken()).toBe('TOKEN_NEW');
  });
});

// 2. Expired token
describe('expired token', () => {
  it('2 endpoint calls; 1 refresh; caller sees success; token updated', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch)
      .mockResolvedValueOnce(response401())
      .mockResolvedValueOnce(refreshSuccess('TOKEN_B'))
      .mockResolvedValueOnce(json200(meUser()));

    const { data, error } = await testClient.GET('/api/v1/me');

    expect(error).toBeUndefined();
    expect(data).toMatchObject({ id: 'u1' });
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3);
    expect(getAccessToken()).toBe('TOKEN_B');
    expect(sessionLossCount).toBe(0);
    const retryReq = vi.mocked(fetch).mock.calls[2]?.[0];
    expect((retryReq as Request | undefined)?.headers.get('Authorization')).toBe('Bearer TOKEN_B');
  });
});

// 3. POST body preserved through retry (testing body-replay mechanics via /logout)
describe('POST body preserved through retry', () => {
  it('method, URL, body, and Authorization all correct on retry', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch)
      .mockResolvedValueOnce(response401())
      .mockResolvedValueOnce(refreshSuccess('TOKEN_B'))
      .mockResolvedValueOnce(json200({ ok: true }));

    await testClient.POST('/api/v1/auth/logout', { body: { scope: 'CURRENT_SESSION' } });

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3);
    const firstCall = vi.mocked(fetch).mock.calls[0]?.[0] as Request;
    const retryCall = vi.mocked(fetch).mock.calls[2]?.[0] as Request;

    expect(firstCall.method).toBe('POST');
    expect(retryCall.method).toBe('POST');
    expect(retryCall.url).toBe(firstCall.url);
    expect(firstCall.headers.get('Authorization')).toBe('Bearer TOKEN_A');
    expect(retryCall.headers.get('Authorization')).toBe('Bearer TOKEN_B');
    expect(await retryCall.clone().json()).toEqual({ scope: 'CURRENT_SESSION' });
  });
});

// 4. Concurrent 401s -> one refresh
describe('concurrent 401s', () => {
  it('N requests; exactly 1 refresh; N retries; no session loss', async () => {
    setAccessToken('TOKEN_A');
    let refreshCount = 0;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCount++;
        await new Promise((r) => setTimeout(r, 5));
        return refreshSuccess('TOKEN_B');
      }
      const auth = req instanceof Request ? req.headers.get('Authorization') : null;
      return auth === 'Bearer TOKEN_B' ? sessions200() : response401();
    });

    const results = await Promise.all([sessions(), sessions(), sessions()]);

    for (const { error } of results) expect(error).toBeUndefined();
    expect(refreshCount).toBe(1);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(7); // 3 initial (401) + 1 refresh + 3 retries (200)
    expect(sessionLossCount).toBe(0);
  });
});

// 5. Stale 401 (token rotated within same session)
describe('stale 401', () => {
  it('detects token already changed; retries with current token; no additional refresh', async () => {
    setAccessToken('TOKEN_A');
    let refreshCount = 0;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCount++;
        return refreshSuccess('TOKEN_C');
      }
      const auth = req instanceof Request ? req.headers.get('Authorization') : null;
      if (auth === 'Bearer TOKEN_A') {
        setAccessToken('TOKEN_B'); // simulate concurrent same-session refresh (no epoch advance)
        return response401();
      }
      return sessions200();
    });

    const { error } = await sessions();

    expect(error).toBeUndefined();
    expect(refreshCount).toBe(0);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 initial (401) + 0 refresh + 1 retry (200)
    expect(sessionLossCount).toBe(0);
  });
});

// 6. Refresh definitive rejection -> exactly one terminal signal
describe('refresh definitive rejection', () => {
  it('N concurrent 401s -> 1 refresh rejected (401) -> exactly 1 session-loss signal', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await Promise.allSettled([sessions(), sessions()]);

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3); // 2 initial (401) + 1 refresh (401)
    expect(sessionLossCount).toBe(1);
  });
});

// 7. Concurrent retry-401
describe('concurrent retry-401', () => {
  it('/me: 1 refresh; N retries all 401; epoch advances once; no global signal', async () => {
    setAccessToken('TOKEN_A');
    let refreshCount = 0;
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCount++;
        await new Promise((r) => setTimeout(r, 5));
        return refreshSuccess('TOKEN_B');
      }
      return response401();
    });

    await Promise.allSettled([testClient.GET('/api/v1/me'), testClient.GET('/api/v1/me'), testClient.GET('/api/v1/me')]);

    expect(refreshCount).toBe(1);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(7); // 3 initial (401) + 1 refresh (200) + 3 retries (401)
    expect(sessionLossCount).toBe(0); // /me suppresses global signal
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // epoch invalidated exactly once
  });

  it('background endpoint: 1 refresh; N retries all 401; exactly 1 terminal signal', async () => {
    setAccessToken('TOKEN_A');
    let refreshCount = 0;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCount++;
        await new Promise((r) => setTimeout(r, 5));
        return refreshSuccess('TOKEN_B');
      }
      return response401();
    });

    await Promise.allSettled([sessions(), sessions(), sessions()]);

    expect(refreshCount).toBe(1);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(7); // 3 initial (401) + 1 refresh (200) + 3 retries (401)
    expect(sessionLossCount).toBe(1);
  });
});

// 8. Cross-login boundary protection
describe('cross-login boundary protection', () => {
  it('epoch changed mid-flight; 401 not retried; no refresh; new session not disturbed', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async () => {
      advanceSessionEpoch(); // logout
      clearAccessToken();
      setAccessToken('TOKEN_B');
      advanceSessionEpoch(); // new login
      return response401();
    });

    const { error } = await sessions();

    expect(error).toBeDefined();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
    expect(currentSessionEpoch()).toBe(epochBefore + 2);
    expect(getAccessToken()).toBe('TOKEN_B');
  });
});

// 9. /me terminal session resolution
describe('/me terminal session resolution', () => {
  it('epoch advances once; global signal NOT invoked', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    const { error } = await testClient.GET('/api/v1/me');

    expect(error).toBeDefined();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 /me (401) + 1 refresh (401)
    expect(sessionLossCount).toBe(0);
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // epoch invalidated
  });

  it('after /me terminal, old-epoch background request does not re-advance epoch or signal', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    // Both dispatched at epoch N; /me processes first (silent), sessions second (global no-op)
    await Promise.allSettled([testClient.GET('/api/v1/me'), sessions()]);

    expect(currentSessionEpoch()).toBe(epochBefore + 1); // exactly once
    expect(sessionLossCount).toBe(0); // /me advanced first; sessions' call is a no-op
  });

  it('terminal non-/me endpoint emits global signal', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await sessions();

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 sessions (401) + 1 refresh (401)
    expect(sessionLossCount).toBe(1);
  });
});

// 10. Refresh operational failures (5xx, 403, and network)
describe('refresh operational failures', () => {
  it('refresh 500 -> operational error thrown; token preserved; no session loss', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return response500();
      return response401();
    });

    await expect(sessions()).rejects.toThrow();

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 sessions (401) + 1 refresh (500)
    expect(getAccessToken()).toBe('TOKEN_A');
    expect(sessionLossCount).toBe(0);
  });

  it('refresh 403 -> operational error thrown; token preserved; no session loss', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        return new Response(JSON.stringify({ status: 403, title: 'Forbidden' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      return response401();
    });

    await expect(sessions()).rejects.toThrow('Token refresh failed: HTTP 403');

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 sessions (401) + 1 refresh (403)
    expect(getAccessToken()).toBe('TOKEN_A');
    expect(sessionLossCount).toBe(0);
  });

  it('refresh network failure -> operational error; token preserved; no session loss', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) throw new TypeError('Network failure');
      return response401();
    });

    await expect(sessions()).rejects.toThrow('Network failure');

    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // 1 sessions (401) + 1 refresh (network error)
    expect(getAccessToken()).toBe('TOKEN_A');
    expect(sessionLossCount).toBe(0);
  });

  it('requestTokenRefresh 401 -> null (definitive rejection); no throw', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response('', { status: 401 }));

    const result = await requestTokenRefresh();

    expect(result).toBeNull();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });

  it('requestTokenRefresh 403 -> throws operational error (not treated as definitive rejection)', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 403, title: 'Forbidden' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' }
      })
    );

    await expect(requestTokenRefresh()).rejects.toThrow('Token refresh failed: HTTP 403');
    expect(sessionLossCount).toBe(0);
  });

  it('requestTokenRefresh 500 -> throws operational error', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response500());

    await expect(requestTokenRefresh()).rejects.toThrow();
    expect(sessionLossCount).toBe(0);
  });
});

// 11. Stale refresh after logout/new login
describe('stale refresh after logout and new login', () => {
  it('old epoch refresh completes; new access token not overwritten; no terminal signal', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        // While refresh is in flight, simulate logout + new login
        advanceSessionEpoch();
        clearAccessToken();
        setAccessToken('TOKEN_B');
        advanceSessionEpoch();
        return refreshSuccess('TOKEN_OLD');
      }
      return response401();
    });

    // /me 401 triggers refresh; refresh sees epoch mismatch and discards TOKEN_OLD
    const { error } = await testClient.GET('/api/v1/me');

    expect(error).toBeDefined(); // 401 returned; old request not retried
    expect(getAccessToken()).toBe('TOKEN_B'); // new login's token preserved
    expect(sessionLossCount).toBe(0); // new session not disturbed
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // /me + /refresh (no retry)
  });
});

// 12. Epoch-scoped refresh deduplication
describe('epoch-scoped refresh deduplication', () => {
  it('requests in same epoch share one refresh', async () => {
    setAccessToken('TOKEN_A');
    let refreshCount = 0;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCount++;
        await new Promise((r) => setTimeout(r, 5));
        return refreshSuccess('TOKEN_B');
      }
      const auth = req instanceof Request ? req.headers.get('Authorization') : null;
      return auth === 'Bearer TOKEN_B' ? sessions200() : response401();
    });

    const results = await Promise.all([sessions(), sessions()]);

    for (const { error } of results) expect(error).toBeUndefined();
    expect(refreshCount).toBe(1);
  });

  it('cross-epoch refresh: separate refresh outcomes, new request succeeds under TOKEN_C, old request not replayed', async () => {
    setAccessToken('TOKEN_A');

    let refreshCalls = 0;
    let refresh1StartedResolve!: () => void;
    const refresh1Started = new Promise<void>((resolve) => {
      refresh1StartedResolve = resolve;
    });

    let resolveRefresh1!: (r: Response) => void;
    const refresh1Gate = new Promise<Response>((resolve) => {
      resolveRefresh1 = resolve;
    });

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCalls++;
        if (refreshCalls === 1) {
          refresh1StartedResolve();
          return refresh1Gate;
        }
        if (refreshCalls === 2) {
          return refreshSuccess('TOKEN_C');
        }
        throw new Error(`Unexpected refresh call ${refreshCalls}`);
      }

      if (url.includes('/api/v1/auth/sessions')) {
        const auth = req instanceof Request ? req.headers.get('Authorization') : null;
        if (auth === 'Bearer TOKEN_C') {
          return sessions200();
        }
        return response401();
      }

      return response500();
    });

    // 1. Old protected request under epoch N / TOKEN_A -> 401 -> refresh #1 starts (kept pending)
    const oldRequestPromise = sessions();
    await refresh1Started;

    // 2. Logical session changes: epoch advances to N+1, TOKEN_B installed
    advanceSessionEpoch();
    clearAccessToken();
    setAccessToken('TOKEN_B');
    advanceSessionEpoch();

    // 3. New protected request under epoch N+1 / TOKEN_B -> 401 -> refresh #2 starts -> returns TOKEN_C -> retries -> success
    const newResponse = await sessions();
    expect(newResponse.error).toBeUndefined();
    expect(newResponse.data).toBeDefined();
    expect(getAccessToken()).toBe('TOKEN_C');

    // 4. Old refresh #1 finally returns TOKEN_OLD
    resolveRefresh1(refreshSuccess('TOKEN_OLD'));
    const oldResponse = await oldRequestPromise;

    // Invariants:
    // - exactly 2 refresh HTTP requests
    expect(refreshCalls).toBe(2);

    // - new request succeeds
    expect(newResponse.error).toBeUndefined();

    // - getAccessToken() == TOKEN_C
    expect(getAccessToken()).toBe('TOKEN_C');

    // - TOKEN_OLD is discarded, old request returned original 401
    expect(oldResponse.error).toBeDefined();

    // - old request is never replayed under TOKEN_B or TOKEN_C
    const sessionAuthHeaders = vi
      .mocked(fetch)
      .mock.calls.filter(([callReq]) => {
        const url = callReq instanceof Request ? callReq.url : String(callReq);
        return url.includes('/api/v1/auth/sessions');
      })
      .map(([callReq]) => (callReq as Request).headers.get('Authorization'));

    expect(sessionAuthHeaders).toEqual([
      'Bearer TOKEN_A', // old request initial dispatch
      'Bearer TOKEN_B', // new request initial dispatch
      'Bearer TOKEN_C' // new request retry
    ]);

    // - no terminal-session signal affects the new session
    expect(sessionLossCount).toBe(0);
  });
});

// 13. Logout command semantics
describe('logout command semantics', () => {
  it('expired token: 401 -> refresh -> retry -> server success; no global signal', async () => {
    setAccessToken('TOKEN_A');

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return refreshSuccess('TOKEN_B');
      const auth = req instanceof Request ? req.headers.get('Authorization') : null;
      if (url.includes('/auth/logout')) {
        return auth === 'Bearer TOKEN_B' ? new Response(null, { status: 204 }) : response401();
      }
      return json200({});
    });

    const { error } = await testClient.POST('/api/v1/auth/logout', { body: { scope: 'CURRENT_SESSION' } });

    expect(error).toBeUndefined();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3);
    expect(sessionLossCount).toBe(0);
  });

  it('terminal recovery failure: epoch invalidated; global handler NOT invoked', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    const { error } = await testClient.POST('/api/v1/auth/logout', { body: { scope: 'CURRENT_SESSION' } });

    expect(error).toBeDefined();
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // epoch invalidated
    expect(sessionLossCount).toBe(0); // caller owns navigation, not global handler
  });
});

// 14. Non-auth errors
describe('non-auth errors', () => {
  it('500 -> returned as-is; no refresh; no session loss', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockResolvedValueOnce(response500());

    const { error } = await sessions();

    expect((error as unknown as { status?: number })?.status).toBe(500);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });

  it('network failure on protected request -> throws; no session loss', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(sessions()).rejects.toThrow('Failed to fetch');
    expect(sessionLossCount).toBe(0);
  });

  it('403 -> returned as-is; no refresh; no session loss', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 403 }), { status: 403, headers: { 'Content-Type': 'application/json' } })
    );

    const { error } = await sessions();

    expect((error as unknown as { status?: number })?.status).toBe(403);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });
});

// 15. Auth endpoints bypass recovery
describe('auth endpoints bypass recovery', () => {
  it('login 401 -> not retried; no refresh', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response401());

    const { error } = await testClient.POST('/api/v1/auth/login', {
      body: { email: 'x@x.com', password: 'bad', refreshTokenDelivery: 'HTTP_ONLY_COOKIE' }
    });

    expect(error).toBeDefined();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });

  it('refresh endpoint 401 -> definitive rejection (null); no recursive refresh', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response('', { status: 401 }));

    const result = await requestTokenRefresh();

    expect(result).toBeNull();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    expect(sessionLossCount).toBe(0);
  });
});

// 16. Session epoch semantics
describe('session epoch', () => {
  it('refresh does NOT advance epoch', async () => {
    const epochBefore = currentSessionEpoch();
    vi.mocked(fetch).mockResolvedValueOnce(refreshSuccess('TOKEN_X'));

    await requestTokenRefresh();

    expect(currentSessionEpoch()).toBe(epochBefore);
  });

  it('terminal session loss on background endpoint advances epoch exactly once', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await sessions();

    expect(currentSessionEpoch()).toBe(epochBefore + 1);
    expect(sessionLossCount).toBe(1);
  });
});

// 17. Session loss handler registration semantics
describe('registerSessionLossHandler', () => {
  it('unregister prevents invocation', async () => {
    let count = 0;
    const unreg = registerSessionLossHandler(() => {
      count++;
    });
    unreg();

    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await sessions();

    expect(count).toBe(0);
    unregister = registerSessionLossHandler(() => {
      sessionLossCount++;
    });
  });

  it('re-registration replaces handler; only latest fires', async () => {
    let count1 = 0;
    let count2 = 0;
    registerSessionLossHandler(() => {
      count1++;
    });
    const unreg2 = registerSessionLossHandler(() => {
      count2++;
    });

    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await sessions();

    expect(count1).toBe(0);
    expect(count2).toBe(1);
    unreg2();
    unregister = registerSessionLossHandler(() => {
      sessionLossCount++;
    });
  });

  it('stale unregister does not clear a later registration (HMR/StrictMode safe)', async () => {
    let count = 0;
    const handler1 = () => {
      count += 10;
    };
    const handler2 = () => {
      count += 1;
    };
    const unreg1 = registerSessionLossHandler(handler1);
    const unreg2 = registerSessionLossHandler(handler2);
    unreg1(); // handler1 !== handler2, so this is a no-op

    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await sessions();

    expect(count).toBe(1);
    unreg2();
    unregister = registerSessionLossHandler(() => {
      sessionLossCount++;
    });
  });
});

// 18. Epoch re-check immediately before request replay
describe('epoch re-check before request replay', () => {
  it('mutation request is not replayed if epoch changed while awaiting refresh recovery', async () => {
    setAccessToken('TOKEN_A');
    let logoutCalls = 0;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/logout')) {
        logoutCalls++;
        return response401();
      }
      if (url.includes('/auth/refresh')) {
        // While refresh recovery is in flight, the logical session advances
        advanceSessionEpoch();
        return refreshSuccess('TOKEN_B');
      }
      return response500();
    });

    const { error } = await testClient.POST('/api/v1/auth/logout', {
      body: { scope: 'CURRENT_SESSION' }
    });

    // Invariant: old logical-session request can never be replayed after its logical session epoch changes
    expect(error).toBeDefined();
    expect(logoutCalls).toBe(1); // exactly 1 call; NEVER replayed under new epoch
    expect(sessionLossCount).toBe(0);
  });
});

// 19. Tightened request-policy matching
describe('tightened request-policy matching', () => {
  it('child route of /me (/api/v1/me/preferences) uses global policy, not route-owned', async () => {
    setAccessToken('TOKEN_A');
    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return new Response('', { status: 401 });
      return response401();
    });

    await (testClient.GET as unknown as (path: string) => Promise<unknown>)('/api/v1/me/preferences');

    // Global policy emits session loss signal
    expect(sessionLossCount).toBe(1);
  });

  it('parameterized protected route (/api/v1/auth/sessions/{familyId}) uses global policy and attaches Bearer token', async () => {
    setAccessToken('TOKEN_A');
    let capturedAuth: string | null = null;

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/api/v1/auth/sessions/sess-123')) {
        capturedAuth = (req as Request).headers.get('Authorization');
        return new Response(null, { status: 204 });
      }
      return response500();
    });

    await testClient.DELETE('/api/v1/auth/sessions/{familyId}', {
      params: { path: { familyId: 'sess-123' } }
    });

    expect(capturedAuth).toBe('Bearer TOKEN_A');
    expect(sessionLossCount).toBe(0);
  });
});

// 20. resolveSession iterative resolution loop
describe('resolveSession', () => {
  it('superseded bootstrap resolution re-resolves and returns authenticated user, not anonymous', async () => {
    expect(getAccessToken()).toBeNull();
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false }
      }
    });

    let resolveBootstrapRefresh!: (r: Response) => void;
    const bootstrapRefreshPromise = new Promise<Response>((res) => {
      resolveBootstrapRefresh = res;
    });

    let refreshCallCount = 0;
    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refreshCallCount++;
        return bootstrapRefreshPromise;
      }
      if (url.includes('/api/v1/me')) {
        const auth = req instanceof Request ? req.headers.get('Authorization') : null;
        if (auth === 'Bearer TOKEN_NEW') {
          return json200(meUser());
        }
        return response401();
      }
      return response500();
    });

    // 1. Bootstrap resolver starts with no token at epoch N
    const resolvePromise = resolveSession(queryClient);

    // Wait until refresh is dispatched
    await new Promise((r) => setTimeout(r, 5));
    expect(refreshCallCount).toBe(1);

    // 2. Logical session changes: TOKEN_NEW installed, epoch advances to N+1
    setAccessToken('TOKEN_NEW');
    advanceSessionEpoch();

    // 3. Old refresh result arrives (superseded)
    resolveBootstrapRefresh(refreshSuccess('TOKEN_OLD'));

    const result = await resolvePromise;

    // Expected: new session is resolved -> authenticated(user), NOT anonymous, NOT cleared
    expect(result).toEqual({ status: 'authenticated', user: meUser() });
    expect(getAccessToken()).toBe('TOKEN_NEW');
  });

  it('bootstrap resolver with definitive rejection in same epoch clears dead session and returns anonymous', async () => {
    expect(getAccessToken()).toBeNull();
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false }
      }
    });

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        return new Response('', { status: 401 });
      }
      return response401();
    });

    const result = await resolveSession(queryClient);

    expect(result).toEqual({ status: 'anonymous' });
    expect(getAccessToken()).toBeNull();
  });

  it('bootstrap resolver with operational 500 error throws without clearing session', async () => {
    expect(getAccessToken()).toBeNull();
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false }
      }
    });

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        return response500();
      }
      return response401();
    });

    await expect(resolveSession(queryClient)).rejects.toThrow();
  });

  it('existing-token resolveSession terminal rejection: 401 -> refresh 401 -> anonymous, 2 calls, token cleared, epoch advanced once, no global handler, no loop', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false }
      }
    });
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/api/v1/me')) return response401();
      if (url.includes('/auth/refresh')) return response401();
      return response500();
    });

    const result = await resolveSession(queryClient);

    expect(result).toEqual({ status: 'anonymous' });
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2); // exactly 1 /me + 1 refresh
    expect(getAccessToken()).toBeNull(); // token cleared
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // epoch advanced once
    expect(sessionLossCount).toBe(0); // no global handler (route-owned)
  });

  it('existing-token /me retry terminal rejection: 401 -> refresh TOKEN_B -> retry 401 -> anonymous, 3 calls, token cleared, epoch advanced once, no global handler', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false }
      }
    });
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return refreshSuccess('TOKEN_B');
      if (url.includes('/api/v1/me')) return response401();
      return response500();
    });

    const result = await resolveSession(queryClient);

    expect(result).toEqual({ status: 'anonymous' });
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(3); // 1 initial /me + 1 refresh + 1 retry /me
    expect(getAccessToken()).toBeNull(); // token cleared
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // epoch advanced once
    expect(sessionLossCount).toBe(0); // no global handler
  });
});

// 21. Stale retry 401 after newer token rotation (dual epoch/token binding)
describe('stale retry 401 after newer token rotation', () => {
  it('retry uses TOKEN_B; current store advances to TOKEN_C; TOKEN_B retry returns 401 -> TOKEN_C preserved, no epoch invalidation, no global signal', async () => {
    setAccessToken('TOKEN_A');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) return refreshSuccess('TOKEN_B');
      if (url.includes('/api/v1/auth/sessions')) {
        const auth = req instanceof Request ? req.headers.get('Authorization') : null;
        if (auth === 'Bearer TOKEN_A') return response401();
        if (auth === 'Bearer TOKEN_B') {
          // While retry with TOKEN_B is in flight, another legitimate same-session refresh installs TOKEN_C
          setAccessToken('TOKEN_C');
          return response401();
        }
      }
      return response500();
    });

    const { error } = await sessions();

    expect(error).toBeDefined();
    expect(getAccessToken()).toBe('TOKEN_C'); // TOKEN_C remains installed!
    expect(currentSessionEpoch()).toBe(epochBefore); // epoch does NOT advance!
    expect(sessionLossCount).toBe(0); // no global session-loss signal!
  });
});

// 22. Superseded refresh results and errors
describe('superseded refresh results and errors', () => {
  it('superseded refresh 500: no operational error affects new session, new token preserved, old request not replayed', async () => {
    setAccessToken('TOKEN_A');

    let resolveOldRefresh!: (r: Response) => void;
    const oldRefreshGate = new Promise<Response>((resolve) => {
      resolveOldRefresh = resolve;
    });

    let refresh1StartedResolve!: () => void;
    const refresh1Started = new Promise<void>((resolve) => {
      refresh1StartedResolve = resolve;
    });

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refresh1StartedResolve();
        return oldRefreshGate;
      }
      return response401();
    });

    // 1. Old request at epoch N triggers refresh
    const oldRequestPromise = sessions();
    await refresh1Started;

    // 2. Epoch changes and new session is established
    advanceSessionEpoch(); // logout
    clearAccessToken();
    setAccessToken('TOKEN_NEW');
    advanceSessionEpoch(); // new login
    const newEpoch = currentSessionEpoch();

    // 3. Old refresh returns 500
    resolveOldRefresh(response500());

    // Old request should settle with original 401 without throwing 500 or replaying
    const oldResult = await oldRequestPromise;
    expect(oldResult.error).toBeDefined();
    expect(getAccessToken()).toBe('TOKEN_NEW'); // new token preserved!
    expect(currentSessionEpoch()).toBe(newEpoch); // epoch unchanged!
    expect(sessionLossCount).toBe(0); // no session loss!
  });

  it('superseded refresh network rejection: no error affects new session, new token preserved, old request not replayed', async () => {
    setAccessToken('TOKEN_A');

    let rejectOldRefresh!: (err: Error) => void;
    const oldRefreshGate = new Promise<Response>((_, reject) => {
      rejectOldRefresh = reject;
    });

    let refresh1StartedResolve!: () => void;
    const refresh1Started = new Promise<void>((resolve) => {
      refresh1StartedResolve = resolve;
    });

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/refresh')) {
        refresh1StartedResolve();
        return oldRefreshGate;
      }
      return response401();
    });

    // 1. Old request at epoch N triggers refresh
    const oldRequestPromise = sessions();
    await refresh1Started;

    // 2. Epoch changes and new session is established
    advanceSessionEpoch();
    clearAccessToken();
    setAccessToken('TOKEN_NEW');
    advanceSessionEpoch();
    const newEpoch = currentSessionEpoch();

    // 3. Old refresh rejects with network error
    rejectOldRefresh(new TypeError('Network failure'));

    // Old request completes without throwing unhandled error or replaying
    const oldResult = await oldRequestPromise;
    expect(oldResult.error).toBeDefined();
    expect(getAccessToken()).toBe('TOKEN_NEW');
    expect(currentSessionEpoch()).toBe(newEpoch);
    expect(sessionLossCount).toBe(0);
  });
});

// 23. Actual logout command terminal recovery
describe('actual logout command terminal recovery', () => {
  it('server logout succeeds: exactly one epoch advance, one local cleanup, caller-owned navigation', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['test'], 'data');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/logout')) return new Response(null, { status: 204 });
      return response500();
    });

    let navigatedTo: string | null = null;
    const fakeNavigate = (to: string) => {
      navigatedTo = to;
    };

    await logoutSession(queryClient);
    fakeNavigate('/login');

    expect(currentSessionEpoch()).toBe(epochBefore + 1); // exactly one epoch advance
    expect(getAccessToken()).toBeNull(); // local cleanup (token)
    expect(queryClient.getQueryData(['test'])).toBeUndefined(); // local cleanup (cache)
    expect(sessionLossCount).toBe(0); // no global navigation signal
    expect(navigatedTo).toBe('/login'); // caller-owned navigation
  });

  it('transport terminal invalidation during logout: exactly one epoch advance (no N+2), one local cleanup, caller-owned navigation', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['test'], 'data');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/logout')) return response401();
      if (url.includes('/auth/refresh')) return response401();
      return response500();
    });

    let navigatedTo: string | null = null;
    const fakeNavigate = (to: string) => {
      navigatedTo = to;
    };

    await logoutSession(queryClient);
    fakeNavigate('/login');

    expect(currentSessionEpoch()).toBe(epochBefore + 1); // exactly one epoch advance (NOT N+2!)
    expect(getAccessToken()).toBeNull(); // local cleanup (token)
    expect(queryClient.getQueryData(['test'])).toBeUndefined(); // local cleanup (cache)
    expect(sessionLossCount).toBe(0); // no global navigation signal
    expect(navigatedTo).toBe('/login'); // caller-owned navigation
  });

  it('server logout failure keeps the local session and cache so the user can retry', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['test'], 'data');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockResolvedValueOnce(response500());

    await expect(logoutSession(queryClient)).rejects.toMatchObject({ status: 500 });
    expect(currentSessionEpoch()).toBe(epochBefore);
    expect(getAccessToken()).toBe('TOKEN_A');
    expect(queryClient.getQueryData(['test'])).toBe('data');
  });

  it('logout network failure keeps the local session and cache so the user can retry', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['test'], 'data');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(logoutSession(queryClient)).rejects.toThrow('Failed to fetch');
    expect(currentSessionEpoch()).toBe(epochBefore);
    expect(getAccessToken()).toBe('TOKEN_A');
    expect(queryClient.getQueryData(['test'])).toBe('data');
  });

  it('new logical session supersedes old logout: does not clear new session or token', async () => {
    setAccessToken('TOKEN_A');
    const queryClient = new QueryClient();
    queryClient.setQueryData(['new-user'], 'active');
    const epochBefore = currentSessionEpoch();

    vi.mocked(fetch).mockImplementation(async (req) => {
      const url = req instanceof Request ? req.url : String(req);
      if (url.includes('/auth/logout')) {
        // While logout is in flight, new session is established
        advanceSessionEpoch();
        setAccessToken('TOKEN_NEW');
        return new Response(null, { status: 204 });
      }
      return response500();
    });

    await logoutSession(queryClient);

    expect(getAccessToken()).toBe('TOKEN_NEW'); // new token preserved!
    expect(queryClient.getQueryData(['new-user'])).toBe('active'); // new cache preserved!
    expect(currentSessionEpoch()).toBe(epochBefore + 1); // not bumped again
  });
});

// 24. API error normalization, message resolution, and presentation
describe('API error normalization and presentation', () => {
  it('normalizes native Error instances preserving message and setting status 500', () => {
    const error = new Error('Network timeout');
    const normalized = normalizeError(error);

    expect(normalized.status).toBe(500);
    expect(normalized.message).toBe('Network timeout');
    expect(normalized.title).toBeUndefined();
    expect(normalized.detail).toBeUndefined();
    expect(normalized.code).toBeUndefined();
    expect(normalized.fieldErrors).toBeUndefined();
    expect(isApiError(normalized)).toBe(true);
  });

  it('normalizes native Error with empty message to generic fallback', () => {
    const error = new Error('');
    const normalized = normalizeError(error);

    expect(normalized.status).toBe(500);
    expect(normalized.message).toBe('An unexpected error occurred');
  });

  it('normalizes primitives (string, null, undefined, number)', () => {
    expect(normalizeError('Direct failure message')).toMatchObject({
      status: 500,
      message: 'Direct failure message'
    });
    expect(normalizeError(null)).toMatchObject({
      status: 500,
      message: 'An unexpected error occurred'
    });
    expect(normalizeError(undefined)).toMatchObject({
      status: 500,
      message: 'An unexpected error occurred'
    });
    expect(normalizeError(404)).toMatchObject({
      status: 500,
      message: 'An unexpected error occurred'
    });
  });

  it('normalizes structured RFC 7807 problem details preserving all fields', () => {
    const raw = {
      status: 400,
      title: 'Bad Request',
      detail: 'Negative balance is prohibited',
      code: 'NEGATIVE_BALANCE_NOT_ALLOWED',
      key: 'error.ledger.negative_balance',
      traceId: 'trace-xyz-123'
    };

    const normalized = normalizeError(raw);

    expect(normalized.status).toBe(400);
    expect(normalized.title).toBe('Bad Request');
    expect(normalized.detail).toBe('Negative balance is prohibited');
    expect(normalized.message).toBe('Negative balance is prohibited');
    expect(normalized.code).toBe('NEGATIVE_BALANCE_NOT_ALLOWED');
    expect(normalized.key).toBe('error.ledger.negative_balance');
    expect(normalized.traceId).toBe('trace-xyz-123');
    expect(normalized.fieldErrors).toBeUndefined();
  });

  it('extracts detail from params.detail when top-level detail is absent', () => {
    const raw = {
      status: 409,
      params: { detail: 'Account balance version conflict' }
    };

    const normalized = normalizeError(raw);

    expect(normalized.status).toBe(409);
    expect(normalized.detail).toBe('Account balance version conflict');
    expect(normalized.message).toBe('Account balance version conflict');
  });

  it('normalizes candidate.fieldErrors array', () => {
    const raw = {
      status: 422,
      fieldErrors: [
        { field: 'amount', key: 'error.amount.positive', detail: 'Amount must be positive' },
        { field: 'currency', detail: 'Currency not supported' }
      ]
    };

    const normalized = normalizeError(raw);

    expect(normalized.fieldErrors).toEqual([
      { field: 'amount', key: 'error.amount.positive', detail: 'Amount must be positive' },
      { field: 'currency', key: undefined, detail: 'Currency not supported' }
    ]);
    expect(normalized.message).toBe('Amount must be positive; Currency not supported');
  });

  it('normalizes candidate.params.errors array when top-level fieldErrors is absent or empty', () => {
    const rawWithMissing = {
      status: 422,
      params: {
        errors: [{ field: 'email', key: 'error.fields.not_blank', detail: 'Email must not be blank' }]
      }
    };

    const normalized1 = normalizeError(rawWithMissing);
    expect(normalized1.fieldErrors).toEqual([{ field: 'email', key: 'error.fields.not_blank', detail: 'Email must not be blank' }]);
    expect(normalized1.message).toBe('Email must not be blank');

    const rawWithEmptyFieldErrors = {
      status: 422,
      fieldErrors: [],
      params: {
        errors: [{ field: 'name', detail: 'Name is required' }]
      }
    };

    const normalized2 = normalizeError(rawWithEmptyFieldErrors);
    expect(normalized2.fieldErrors).toEqual([{ field: 'name', key: undefined, detail: 'Name is required' }]);
    expect(normalized2.message).toBe('Name is required');
  });

  it('sets fieldErrors to undefined when empty arrays are supplied', () => {
    const raw = { status: 400, fieldErrors: [], params: { errors: [] } };
    const normalized = normalizeError(raw);
    expect(normalized.fieldErrors).toBeUndefined();
  });

  it('prevents double normalization of already-normalized ApiError', () => {
    const raw = { status: 409, detail: 'Conflict', code: 'VERSION_CONFLICT' };
    const first = normalizeError(raw);
    const second = normalizeError(first);

    expect(second).toBe(first);
    expect(isApiError(second)).toBe(true);
  });

  it('resolves user-facing error message with proper precedence via getApiErrorMessage', () => {
    const withFieldErrors = normalizeError({
      status: 422,
      detail: 'Validation failed',
      fieldErrors: [{ field: 'email', detail: 'Email already exists' }]
    });
    expect(getApiErrorMessage(withFieldErrors, 'Fallback')).toBe('Email already exists');

    const withDetailOnly = normalizeError({
      status: 400,
      detail: 'Specific domain detail'
    });
    expect(getApiErrorMessage(withDetailOnly, 'Fallback')).toBe('Specific domain detail');

    const withGenericOnly = normalizeError({ status: 500 });
    expect(getApiErrorMessage(withGenericOnly, 'Custom contextual fallback')).toBe('Custom contextual fallback');

    const withoutFallback = normalizeError({ status: 500 });
    expect(getApiErrorMessage(withoutFallback)).toBe('An unexpected error occurred');
  });

  it('shows Mantine notification and returns normalized error via showApiError', () => {
    const showSpy = vi.spyOn(notifications, 'show');

    const raw = { status: 403, detail: 'Permission denied', code: 'FORBIDDEN' };
    const returned = showApiError(raw, { title: 'Access Denied' });

    expect(returned.code).toBe('FORBIDDEN');
    expect(returned.detail).toBe('Permission denied');
    expect(showSpy).toHaveBeenCalledTimes(1);
    expect(showSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Access Denied',
        message: 'Permission denied',
        color: 'red'
      })
    );

    // Calling showApiError with already normalized error does not double normalize
    showSpy.mockClear();
    const returnedAgain = showApiError(returned, { title: 'Access Denied' });
    expect(returnedAgain).toBe(returned);
    expect(showSpy).toHaveBeenCalledTimes(1);
  });
});
