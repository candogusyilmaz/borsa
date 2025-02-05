import { type UseMutationResult, useMutation } from '@tanstack/react-query';
import { type ReactNode, createContext, useContext, useState } from 'react';
import { http } from './axios.ts';

export interface LoginResponse {
  token: string;
  permissions: string[];
}

export interface AuthContext {
  isAuthenticated: boolean;
  login: UseMutationResult<
    LoginResponse,
    Error,
    | {
        username: string;
        password: string;
      }
    | { token: string },
    unknown
  >;
  logout: () => Promise<void>;
  updateToken: (token: string) => void;
}

type AuthProviderProps = {
  children: ReactNode;
};

function getStoredUser(): LoginResponse | null {
  const user = localStorage.getItem('user');

  return user ? JSON.parse(user) : null;
}

const AuthContext = createContext<AuthContext>(null!);

export function AuthenticationProvider({ children }: Readonly<AuthProviderProps>) {
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

  const logout = async () => {
    localStorage.removeItem('user');
    setUser(null);
  };

  const updateToken = (token: string) => {
    const user = getStoredUser();
    user!.token = token;
    localStorage.setItem('user', JSON.stringify(user));
    setUser(user);
  };

  return (
    <AuthContext
      value={{
        isAuthenticated: !!user,
        login,
        logout,
        updateToken
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
