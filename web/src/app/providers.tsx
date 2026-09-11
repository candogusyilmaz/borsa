import { localStorageColorSchemeManager, MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useCallback, useEffect, useMemo } from 'react';
import { advanceSessionEpoch, registerSessionLossHandler, setAccessToken } from '@/api/auth-state';
import { client } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { clearLocalSession, fetchCurrentUser, logoutSession } from '@/api/session';
import { queryClient } from '@/app/query-client';
import { router } from '@/app/router';
import { AuthContext } from '@/shared/hooks/use-auth';
import type { AuthContextValue } from '@/shared/types/auth';
import { cssVariablesResolver } from '@/theme/css-variables';
import { theme } from '@/theme/theme';

export { queryClient };

interface ProvidersProps {
  children: ReactNode;
}

function AuthProvider({ children }: { children: ReactNode }) {
  const logout = useCallback(async () => {
    await logoutSession(queryClient);
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
      advanceSessionEpoch();

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
    const unregister = registerSessionLossHandler(async () => {
      clearLocalSession(queryClient);

      await router.navigate({
        to: '/login',
        replace: true
      });
    });

    return unregister;
  }, []);

  const contextValue = useMemo<AuthContextValue>(
    () => ({
      login,
      logout
    }),
    [login, logout]
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}

const colorSchemeManager = localStorageColorSchemeManager({ key: 'app-color-scheme' });

export function Providers({ children }: ProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <MantineProvider
        theme={theme}
        cssVariablesResolver={cssVariablesResolver}
        colorSchemeManager={colorSchemeManager}
        defaultColorScheme="auto">
        <Notifications position="top-right" />

        <AuthProvider>{children}</AuthProvider>
      </MantineProvider>
    </QueryClientProvider>
  );
}
