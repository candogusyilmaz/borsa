import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useCallback, useEffect, useMemo } from 'react';
import { client, getAccessToken, normalizeError, registerAuthFailureHandler, requestTokenRefresh, setAccessToken } from '@/api/client';
import { clearLocalSession, fetchCurrentUser } from '@/api/session';
import { queryClient } from '@/app/query-client';
import { router } from '@/app/router';
import { AuthContext } from '@/shared/hooks/use-auth';
import type { AuthContextValue } from '@/shared/types/auth';

export { queryClient };

interface ProvidersProps {
  children: ReactNode;
}

/**
 * Router owns session resolution (see /_authenticated and /login beforeLoad).
 * AuthProvider only supplies the login/logout commands and the temporary
 * background session-loss handler (Stage 5 owns its redesign).
 */
function AuthProvider({ children }: { children: ReactNode }) {
  const logout = useCallback(async () => {
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
    }
  }, []);

  const login = useCallback(async (credentials: { email: string; password: string }) => {
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
      sessionEstablished = true;

      await fetchCurrentUser(queryClient);
    } catch (error) {
      if (sessionEstablished) {
        clearLocalSession(queryClient);
      }
      throw normalizeError(error);
    }
  }, []);

  useEffect(() => {
    registerAuthFailureHandler(async () => {
      // Invoked when a protected request 401s before any recovery attempt.
      // Restore through the refresh cookie; if that fails the session is
      // terminally gone.
      const restored = await requestTokenRefresh();
      if (restored) {
        return;
      }

      clearLocalSession(queryClient);
      await router.navigate({ to: '/login', replace: true });
    });
  }, []);

  const contextValue = useMemo<AuthContextValue>(() => ({ login, logout }), [login, logout]);

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
