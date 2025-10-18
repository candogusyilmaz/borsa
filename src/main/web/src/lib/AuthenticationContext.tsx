import { type UseMutationResult, useMutation, useQueryClient } from '@tanstack/react-query';
import { createContext, type ReactNode, useContext, useState } from 'react';
import { http } from './axios.ts';
import { sleep } from './sleep.ts';

export interface LoginResponse {
  name: string;
  email: string;
  token: string;
  permissions: string[];
}

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
  updateToken: (token: string) => void;
  refreshToken: () => Promise<void>;
}

type AuthProviderProps = {
  children: ReactNode;
};

function getStoredUser(): LoginResponse | null {
  try {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  } catch {
    return null;
  }
}

const AuthContext = createContext<AuthContext>(null!);

export function AuthenticationProvider({ children }: Readonly<AuthProviderProps>) {
  const client = useQueryClient();
  const [user, setUser] = useState<LoginResponse | null>(getStoredUser());

  const login = useMutation({
    mutationFn: async (credentials: { username: string; password: string } | { token: string }) => {
      const endpoint = 'token' in credentials ? '/auth/google' : '/auth/token';
      return (await http.post<LoginResponse>(endpoint, credentials)).data;
    },
    onSuccess: async (data) => {
      localStorage.setItem('user', JSON.stringify(data));
      setUser(data);
    }
  });

  const register = useMutation({
    mutationFn: async (credentials: { name: string; email: string; password: string }) => {
      return (await http.post<LoginResponse>('/auth/register', credentials)).data;
    },
    onSuccess: async (data) => {
      localStorage.setItem('user', JSON.stringify(data));
      setUser(data);
    }
  });

  const logout = async () => {
    localStorage.removeItem('user');
    client.removeQueries();
    setUser(null);
    await sleep(1);
  };

  const updateToken = (token: string) => {
    const user = getStoredUser();
    user!.token = token;
    localStorage.setItem('user', JSON.stringify(user));
    setUser(user);
  };

  const refreshToken = async () => {
    if (!user) return;

    try {
      const response = await http.post<{ token: string }>('/auth/refresh-token', { token: user.token });
      updateToken(response.data.token);
    } catch (error) {
      console.error('Failed to refresh token', error);
      logout();
    }
  };

  return (
    <AuthContext
      value={{
        user,
        isAuthenticated: !!user,
        login,
        register,
        logout,
        updateToken,
        refreshToken
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
