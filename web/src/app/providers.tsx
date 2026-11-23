import { Notifications } from '@mantine/notifications';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuthenticationProvider, useAuthentication } from '~/features/auth/authentication-context';
import { LocaleProvider, useLocale } from '~/shared/i18n/locale-context';
import { AppThemeProvider } from '~/theme';
import { queryClient } from './query-client';

function UserLocaleProvider({ children }: { children: ReactNode }) {
  const { user } = useAuthentication();
  return <LocaleProvider user={user}>{children}</LocaleProvider>;
}

function GoogleServices({ children }: { children: ReactNode }) {
  const { language } = useLocale();
  return (
    <GoogleOAuthProvider clientId="11207596743-8ql23qmgre6sssabg68hvsrcioucd686.apps.googleusercontent.com" locale={language}>
      {children}
    </GoogleOAuthProvider>
  );
}

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthenticationProvider>
        <AppThemeProvider>
          <UserLocaleProvider>
            <Notifications position="top-right" />
            <GoogleServices>{children}</GoogleServices>
          </UserLocaleProvider>
        </AppThemeProvider>
      </AuthenticationProvider>
    </QueryClientProvider>
  );
}
