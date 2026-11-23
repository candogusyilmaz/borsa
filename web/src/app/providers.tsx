import { DatesProvider } from '@mantine/dates';
import { Notifications } from '@mantine/notifications';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuthenticationProvider } from '~/features/auth/authentication-context';
import { AppThemeProvider } from '~/theme';
import { queryClient } from './query-client';

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <GoogleOAuthProvider clientId="11207596743-8ql23qmgre6sssabg68hvsrcioucd686.apps.googleusercontent.com">
      <QueryClientProvider client={queryClient}>
        <AuthenticationProvider>
          <AppThemeProvider>
            <Notifications position="top-right" />
            <DatesProvider settings={{}}>{children}</DatesProvider>
          </AppThemeProvider>
        </AuthenticationProvider>
      </QueryClientProvider>
    </GoogleOAuthProvider>
  );
}
