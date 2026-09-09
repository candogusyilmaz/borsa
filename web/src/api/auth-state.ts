const STORAGE_KEY = 'stocks_access_token';

let memoryAccessToken: string | null = null;

export function getAccessToken(): string | null {
  if (memoryAccessToken) {
    return memoryAccessToken;
  }

  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);

    if (stored) {
      memoryAccessToken = stored;
      return stored;
    }
  } catch {
    // sessionStorage unavailable.
  }

  return null;
}

export function setAccessToken(token: string | null): void {
  memoryAccessToken = token;

  try {
    if (token) {
      sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // sessionStorage unavailable.
  }
}

export function clearAccessToken(): void {
  setAccessToken(null);
}

// Identifies the current logical authenticated session.
//
// Refreshing an access token does not change this value.
// Login, logout and terminal session invalidation do.
let sessionEpoch = 0;

export function currentSessionEpoch(): number {
  return sessionEpoch;
}

export function advanceSessionEpoch(): void {
  sessionEpoch++;
}

type SessionLossHandler = () => void | Promise<void>;

let sessionLossHandler: SessionLossHandler | null = null;

export function registerSessionLossHandler(handler: SessionLossHandler): () => void {
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

/**
 * Invalidates a terminally rejected logical session.
 *
 * Both the epoch and token must still match. If either changed, the
 * authentication result belongs to stale work and must not affect the
 * current session.
 */
export function invalidateSession({ expectedEpoch, expectedToken, notify }: InvalidateSessionOptions): boolean {
  if (currentSessionEpoch() !== expectedEpoch) {
    return false;
  }

  if (getAccessToken() !== expectedToken) {
    return false;
  }

  clearAccessToken();
  advanceSessionEpoch();

  if (notify) {
    void sessionLossHandler?.();
  }

  return true;
}
