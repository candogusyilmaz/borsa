import type { paths } from './schema';

export type AuthSession = paths['/api/auth/token']['post']['responses']['200']['content']['*/*'];

export type SessionEvent = { type: 'UPDATED'; session: AuthSession } | { type: 'CLEARED' };

const STORAGE_KEY = 'user';
const CHANNEL_NAME = 'canverse_auth_sync';
const DEFAULT_BUFFER_MS = 60_000;

const listeners = new Set<(event: SessionEvent) => void>();
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(CHANNEL_NAME) : null;

channel?.addEventListener('message', (event: MessageEvent<SessionEvent>) => {
  notifyLocal(event.data);
});

function notifyLocal(event: SessionEvent): void {
  for (const listener of listeners) {
    listener(event);
  }
}

export function getJwtExpirationMs(token: string): number | null {
  try {
    const payloadPart = token.split('.')[1];
    if (!payloadPart) return null;

    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: number };

    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function isTokenExpiringSoon(token: string, bufferMs = DEFAULT_BUFFER_MS): boolean {
  const expMs = getJwtExpirationMs(token);
  if (expMs === null) return true;

  return Date.now() + bufferMs >= expMs;
}

export const sessionStore = {
  getSession(): AuthSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;

      const parsed = JSON.parse(raw) as AuthSession;
      if (typeof parsed?.token !== 'string' || parsed.token.length === 0) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }

      // Access token süresi dolmuş olsa bile silme — HttpOnly refresh cookie ile yenilenecek
      return parsed;
    } catch {
      return null;
    }
  },

  getToken(): string | null {
    return this.getSession()?.token ?? null;
  },

  setSession(session: AuthSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    const event: SessionEvent = { type: 'UPDATED', session };
    notifyLocal(event);
    channel?.postMessage(event);
  },

  clearSession(): void {
    localStorage.removeItem(STORAGE_KEY);
    const event: SessionEvent = { type: 'CLEARED' };
    notifyLocal(event);
    channel?.postMessage(event);
  },

  subscribe(listener: (event: SessionEvent) => void): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }
};
