import { googleLogout } from '@react-oauth/google';
import { type UseMutationResult, useMutation, useQueryClient } from '@tanstack/react-query';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { AUTH_REQUEST_TIMEOUT_MS, withAuthLock } from '~/api/auth-refresh';
import { unwrapApiResponse } from '~/api/errors';
import { client } from '~/api/openapi';
import { type AuthSession, sessionStore } from '~/api/token';

export interface AuthContext {
  user: AuthSession | null;
  isAuthenticated: boolean;
  login: UseMutationResult<AuthSession, unknown, { username: string; password: string } | { code: string; redirectUri?: string }, unknown>;
  register: UseMutationResult<AuthSession, unknown, { name: string; email: string; password: string }, unknown>;
  logout: () => Promise<void>;
}

type AuthProviderProps = {
  children: ReactNode;
};

const AuthContext = createContext<AuthContext>(null!);

export function AuthenticationProvider({ children }: Readonly<AuthProviderProps>) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState(sessionStore.initialSession);

  useEffect(() => {
    function syncSession() {
      const session = sessionStore.getSession();
      if (sessionStore.acceptSession(session)) {
        setUser(session);
        return;
      }

      queryClient.clear();
      if (!session) googleLogout();
      window.location.replace(session ? '/dashboard' : '/login');
    }

    const unsubscribe = sessionStore.subscribe(syncSession);
    syncSession();
    return unsubscribe;
  }, [queryClient]);

  const email = user?.email;
  const logout = useCallback(async () => {
    if (!email) return;

    await withAuthLock(async () => {
      if (sessionStore.getSession()?.email !== email) return;

      try {
        await client.POST('/api/auth/logout', { signal: AbortSignal.timeout(AUTH_REQUEST_TIMEOUT_MS) });
      } catch {
        // Local sign-out still works when the server is unreachable.
      } finally {
        if (sessionStore.getSession()?.email === email) sessionStore.clearSession();
      }
    });
  }, [email]);

  const login = useMutation({
    mutationFn: async (credentials: { username: string; password: string } | { code: string; redirectUri?: string }) => {
      const endpoint = 'code' in credentials ? '/api/auth/google' : '/api/auth/token';
      return unwrapApiResponse(await client.POST(endpoint, { body: credentials }));
    },
    onSuccess: (data) => {
      sessionStore.setSession(data);
    }
  });

  const register = useMutation({
    mutationFn: async (credentials: { name: string; email: string; password: string }) => {
      return unwrapApiResponse(await client.POST('/api/auth/register', { body: credentials }));
    },
    onSuccess: (data) => {
      sessionStore.setSession(data);
    }
  });

  return (
    <AuthContext
      value={{
        user,
        isAuthenticated: Boolean(user),
        login,
        register,
        logout
      }}>
      {children}
    </AuthContext>
  );
}

export function useAuthentication() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuthentication must be used within an AuthenticationProvider');
  }

  return context;
}
