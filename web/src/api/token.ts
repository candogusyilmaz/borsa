import type { paths } from './schema';

export type AuthSession = paths['/api/auth/token']['post']['responses']['200']['content']['*/*'];

const STORAGE_KEY = 'user';
const listeners = new Set<() => void>();

function notifyLocal(): void {
  for (const listener of listeners) listener();
}

function readSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;

    const session = JSON.parse(raw) as AuthSession | null;
    return typeof session?.email === 'string' && session.email && typeof session.token === 'string' && session.token ? session : null;
  } catch {
    return null;
  }
}

const initialSession = readSession();
// A document stays bound to its first signed-in account until navigation replaces it.
let documentEmail = initialSession?.email;

export const sessionStore = {
  initialSession,
  getSession: readSession,

  acceptSession(session: AuthSession | null): boolean {
    if (documentEmail && session?.email !== documentEmail) return false;
    documentEmail ??= session?.email;
    return true;
  },

  getRequestSession(): AuthSession | null {
    const session = readSession();
    if (documentEmail && session?.email !== documentEmail) {
      throw new DOMException('The signed-in account changed', 'AbortError');
    }
    return session;
  },

  setSession(session: AuthSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    notifyLocal();
  },

  clearSession(): void {
    localStorage.removeItem(STORAGE_KEY);
    notifyLocal();
  },

  subscribe(listener: () => void): () => void {
    function onStorage(event: StorageEvent) {
      if (event.storageArea === localStorage && (event.key === STORAGE_KEY || event.key === null)) listener();
    }

    listeners.add(listener);
    window.addEventListener('storage', onStorage);
    return () => {
      listeners.delete(listener);
      window.removeEventListener('storage', onStorage);
    };
  }
};
