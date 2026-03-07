import { type UseMutationResult, useMutation, useQueryClient } from '@tanstack/react-query';
import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { client } from '~/api/openapi.ts';
import type { paths } from '~/api/schema.d.ts';
import { sleep } from './sleep.ts';

type LoginResponse = paths['/api/auth/token']['post']['responses']['200']['content']['*/*'];

export interface AuthContext {
  user: LoginResponse | null;
  isAuthenticated: boolean;
  login: UseMutationResult<
    LoginResponse,
    unknown,
    | {
        username: string;
        password: string;
      }
    | { token: string },
    unknown
  >;
  register: UseMutationResult<LoginResponse, unknown, { name: string; email: string; password: string }, unknown>;
  logout: () => Promise<void>;
}

type AuthProviderProps = {
  children: ReactNode;
};

function isTokenValid(token: string): boolean {
  try {
    const base64 = token.split('.')[1]!.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return typeof payload.exp !== 'number' || payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

function getTokenExpiryMs(token: string): number | null {
  try {
    const base64 = token.split('.')[1]!.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return typeof payload.exp === 'number' ? payload.exp * 1000 - Date.now() : null;
  } catch {
    return null;
  }
}

function getStoredUser(): LoginResponse | null {
  try {
    const stored = localStorage.getItem('user');
    if (!stored) return null;
    const parsed = JSON.parse(stored) as LoginResponse;
    if (!isTokenValid(parsed.token)) {
      localStorage.removeItem('user');
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

const AuthContext = createContext<AuthContext>(null!);

export function AuthenticationProvider({ children }: Readonly<AuthProviderProps>) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<LoginResponse | null>(getStoredUser());

  const logout = useMemo(
    () => async () => {
      localStorage.removeItem('user');
      queryClient.removeQueries();
      setUser(null);
      await sleep(1);
    },
    [queryClient]
  );

  useEffect(() => {
    if (!user) return;

    const msUntilExpiry = getTokenExpiryMs(user.token);

    if (msUntilExpiry === null) return;

    if (msUntilExpiry <= 0) {
      logout();
      return;
    }

    const timer = setTimeout(() => logout(), msUntilExpiry);

    return () => clearTimeout(timer);
  }, [user, logout]);

  const login = useMutation({
    mutationFn: async (credentials: { username: string; password: string } | { token: string }) => {
      const endpoint = 'token' in credentials ? '/api/auth/google' : '/api/auth/token';
      return (await client.POST(endpoint, { body: credentials })).data!;
    },
    onSuccess: async (data) => {
      localStorage.setItem('user', JSON.stringify(data));
      setUser(data);
    }
  });

  const register = useMutation({
    mutationFn: async (credentials: { name: string; email: string; password: string }) => {
      return (await client.POST('/api/auth/register', { body: credentials })).data!;
    },
    onSuccess: async (data) => {
      localStorage.setItem('user', JSON.stringify(data));
      setUser(data);
    }
  });

  return (
    <AuthContext
      value={{
        user,
        isAuthenticated: !!user && isTokenValid(user.token),
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
    throw new Error('useAuth must be used within an AuthProvider');
  }

  return context;
}
