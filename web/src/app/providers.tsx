import { Center, Loader, MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useEffect, useState } from 'react';
import { $api, clearAccessToken, client, getAccessToken, normalizeError, registerAuthFailureHandler, setAccessToken } from '@/api/client';
import { queryClient } from '@/app/query-client';
import { router } from '@/app/router';
import { AuthContext } from '@/shared/hooks/use-auth';
import type { AuthContextValue, User } from '@/shared/types/auth';

export { queryClient };

async function requestTokenRefresh() {
  try {
    const { data, error } = await client.POST('/api/v1/auth/refresh', {
      body: {
        refreshTokenDelivery: 'HTTP_ONLY_COOKIE'
      }
    });

    if (error || !data?.accessToken) {
      return null;
    }

    return data.accessToken;
  } catch {
    return null;
  }
}

interface ProvidersProps {
  children: ReactNode;
}

function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => getAccessToken());
  const [isInitializing, setIsInitializing] = useState(true);

  const meQuery = $api.useQuery('get', '/api/v1/me', undefined, {
    enabled: Boolean(token),
    retry: false
  });

  async function refreshSession() {
    const newAccessToken = await requestTokenRefresh();
    if (!newAccessToken) {
      clearAccessToken();
      setToken(null);
      queryClient.clear();
      return false;
    }

    setAccessToken(newAccessToken);
    setToken(newAccessToken);
    try {
      await queryClient.fetchQuery($api.queryOptions('get', '/api/v1/me'));
      return true;
    } catch {
      clearAccessToken();
      setToken(null);
      queryClient.clear();
      return false;
    }
  }

  async function logout() {
    try {
      if (getAccessToken()) {
        await client.POST('/api/v1/auth/logout', {
          body: {
            scope: 'CURRENT_SESSION'
          }
        });
      }
    } catch {
      // Ignore network errors during logout
    } finally {
      clearAccessToken();
      setToken(null);
      queryClient.clear();
      await router.invalidate();
      await router.navigate({ to: '/login' });
    }
  }

  async function login(credentials: { email: string; password: string }) {
    const { data, error } = await client.POST('/api/v1/auth/login', {
      body: {
        email: credentials.email,
        password: credentials.password,
        deviceLabel: 'Web Browser',
        refreshTokenDelivery: 'HTTP_ONLY_COOKIE'
      }
    });

    if (error || !data) {
      throw normalizeError(error);
    }

    setAccessToken(data.accessToken);
    setToken(data.accessToken);
    try {
      const currentUser = await queryClient.fetchQuery($api.queryOptions('get', '/api/v1/me'));
      if (!currentUser) {
        throw new Error('Failed to retrieve user profile after login');
      }
      await router.invalidate();
    } catch (profileError) {
      clearAccessToken();
      setToken(null);
      queryClient.clear();
      await router.invalidate();
      throw normalizeError(profileError);
    }
  }

  useEffect(() => {
    registerAuthFailureHandler(async () => {
      clearAccessToken();
      setToken(null);
      queryClient.clear();
      await router.invalidate();
      await router.navigate({ to: '/login' });
    });

    async function initAuth() {
      try {
        const storedToken = getAccessToken();
        if (storedToken) {
          try {
            await queryClient.fetchQuery($api.queryOptions('get', '/api/v1/me'));
            return;
          } catch {
            clearAccessToken();
            setToken(null);
            queryClient.removeQueries({ queryKey: ['get', '/api/v1/me'] });
          }
        }

        const newAccessToken = await requestTokenRefresh();
        if (newAccessToken) {
          setAccessToken(newAccessToken);
          setToken(newAccessToken);
          try {
            await queryClient.fetchQuery($api.queryOptions('get', '/api/v1/me'));
          } catch {
            clearAccessToken();
            setToken(null);
            queryClient.removeQueries({ queryKey: ['get', '/api/v1/me'] });
          }
        } else {
          clearAccessToken();
          setToken(null);
        }
      } finally {
        setIsInitializing(false);
      }
    }

    initAuth();
  }, []);

  const cachedUser = queryClient.getQueryData<User>(['get', '/api/v1/me']);
  const activeToken = token || getAccessToken();
  const user = activeToken ? (meQuery.data ?? cachedUser ?? null) : null;
  const isAuthenticated = Boolean(activeToken && (user || cachedUser));
  const isLoading = isInitializing || (Boolean(token) && meQuery.isLoading);

  const contextValue: AuthContextValue = {
    user,
    isAuthenticated,
    isLoading,
    login,
    logout,
    refreshSession
  };

  if (isLoading) {
    return (
      <Center h="100vh">
        <Loader size="xl" type="dots" />
      </Center>
    );
  }

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}

export function Providers({ children }: ProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <MantineProvider>
        <Notifications position="top-right" />
        <AuthProvider>{children}</AuthProvider>
      </MantineProvider>
    </QueryClientProvider>
  );
}
