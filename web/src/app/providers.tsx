import { Center, Loader, MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import {
  $api,
  client,
  getAccessToken,
  normalizeError,
  registerAuthFailureHandler,
  requestTokenRefresh,
  setAccessToken
} from '@/api/client';
import { clearLocalSession, fetchCurrentUser, isAbortError, ME_QUERY_KEY, resolveSession } from '@/api/session';
import { queryClient } from '@/app/query-client';
import { router } from '@/app/router';
import { AuthContext } from '@/shared/hooks/use-auth';
import type { AuthContextValue, User } from '@/shared/types/auth';

export { queryClient };

interface ProvidersProps {
  children: ReactNode;
}

function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => getAccessToken());
  const [isInitializing, setIsInitializing] = useState(true);
  const isInitializingRef = useRef(true);

  const meQuery = $api.useQuery('get', '/api/v1/me', undefined, {
    enabled: Boolean(token) && !isInitializing,
    retry: false
  });

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
      clearLocalSession(queryClient);
      setToken(null);
    }
  }

  async function login(credentials: { email: string; password: string }) {
    let sessionEstablished = false;
    try {
      const { data, error } = await client.POST('/api/v1/auth/login', {
        body: {
          email: credentials.email,
          password: credentials.password,
          deviceLabel: 'Web Browser',
          refreshTokenDelivery: 'HTTP_ONLY_COOKIE'
        }
      });

      if (error || !data) {
        throw error ?? new Error('Login request failed');
      }

      setAccessToken(data.accessToken);
      setToken(data.accessToken);
      sessionEstablished = true;

      await fetchCurrentUser(queryClient);
    } catch (error) {
      if (sessionEstablished) {
        clearLocalSession(queryClient);
        setToken(null);
      }
      throw normalizeError(error);
    }
  }

  useEffect(() => {
    let isCancelled = false;

    registerAuthFailureHandler(async () => {
      if (isCancelled || isInitializingRef.current) {
        return;
      }

      const newAccessToken = await requestTokenRefresh();
      if (newAccessToken) {
        setToken(newAccessToken);
        return;
      }

      clearLocalSession(queryClient);
      setToken(null);
      await router.navigate({ to: '/login', replace: true });
    });

    async function initAuth() {
      try {
        const resolution = await resolveSession(queryClient);
        if (isCancelled) {
          return;
        }
        if (resolution.status === 'authenticated') {
          // Mirror the session-layer access token into temporary React state.
          setToken(getAccessToken());
        } else {
          setToken(null);
        }
      } catch (error) {
        if (isCancelled) {
          return;
        }
        if (isAbortError(error)) {
          return;
        }
        // Operational failure propagated from resolveSession (e.g. /me 5xx or
        // network): the session is not known to be invalid, so it must not be
        // destroyed. Mirror the stored token so the app renders and the /me
        // query surfaces or recovers from the error reactively.
        setToken(getAccessToken());
      } finally {
        if (!isCancelled) {
          isInitializingRef.current = false;
          setIsInitializing(false);
        }
      }
    }

    initAuth();

    return () => {
      isCancelled = true;
    };
  }, []);

  const cachedUser = queryClient.getQueryData<User>(ME_QUERY_KEY);
  const activeToken = token || getAccessToken();
  const user = activeToken ? (meQuery.data ?? cachedUser ?? null) : null;
  const isAuthenticated = Boolean(activeToken);
  const isLoading = isInitializing || (Boolean(token) && meQuery.isLoading);

  const contextValue: AuthContextValue = {
    user,
    isAuthenticated,
    isLoading,
    login,
    logout
  };

  if (isInitializing) {
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
