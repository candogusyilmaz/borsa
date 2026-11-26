import { googleLogout } from '@react-oauth/google';
import { type UseMutationResult, useMutation, useQueryClient } from '@tanstack/react-query';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { getValidAccessToken } from '~/api/auth-refresh';
import { client } from '~/api/openapi';
import { type AuthSession, isTokenExpiringSoon, sessionStore } from '~/api/token';

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
  const [user, setUser] = useState<AuthSession | null>(() => sessionStore.getSession());

  useEffect(() => {
    return sessionStore.subscribe((event) => {
      if (event.type === 'UPDATED') {
        setUser(event.session);
        return;
      }

      setUser(null);
      googleLogout();
      queryClient.clear();
    });
  }, [queryClient]);

  useEffect(() => {
    const refreshIfNeeded = () => {
      const current = sessionStore.getSession();
      if (current && isTokenExpiringSoon(current.token)) {
        void getValidAccessToken().catch(() => {});
      }
    };

    refreshIfNeeded();

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        refreshIfNeeded();
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => document.removeEventListener('visibilitychange', onVisibilityChange);
  }, []);

  const logout = useCallback(async () => {
    sessionStore.clearSession();
  }, []);

  const login = useMutation({
    mutationFn: async (credentials: { username: string; password: string } | { code: string; redirectUri?: string }) => {
      const endpoint = 'code' in credentials ? '/api/auth/google' : '/api/auth/token';
      const { data, error } = await client.POST(endpoint, { body: credentials });
      if (error || !data) {
        throw { response: { data: error } };
      }
      return data;
    },
    onSuccess: (data) => {
      sessionStore.setSession(data);
    }
  });

  const register = useMutation({
    mutationFn: async (credentials: { name: string; email: string; password: string }) => {
      const { data, error } = await client.POST('/api/auth/register', { body: credentials });
      if (error || !data) {
        throw { response: { data: error } };
      }
      return data;
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
