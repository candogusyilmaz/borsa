import { MantineProvider } from '@mantine/core';
import { DatesProvider } from '@mantine/dates';
import { Notifications } from '@mantine/notifications';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRouter, RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthenticationProvider, useAuthentication } from '~/lib/AuthenticationContext.tsx';
import { AxiosProvider } from '~/lib/axios.ts';
import { routeTree } from '~/routeTree.gen';

import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/charts/styles.css';
import '@mantine/spotlight/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/dropzone/styles.css';

import '~/styles/global.css';
import { NetworkError } from './components/network-error/network-error';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 0,
      staleTime: 30000
    }
  }
});

const router = createRouter({
  routeTree,
  defaultErrorComponent: ({ error }) => <NetworkError error={error} />,
  context: {
    auth: undefined!,
    queryClient
  },
  defaultPreload: 'intent'
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

function Main() {
  const auth = useAuthentication();

  return (
    <RouterProvider
      router={router}
      context={{ auth, queryClient }}
      InnerWrap={({ children }) => <AxiosProvider>{children}</AxiosProvider>}
    />
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GoogleOAuthProvider clientId="11207596743-8ql23qmgre6sssabg68hvsrcioucd686.apps.googleusercontent.com">
      <QueryClientProvider client={queryClient}>
        <AuthenticationProvider>
          <MantineProvider
            defaultColorScheme="dark"
            theme={{
              defaultRadius: 'md',
              fontFamily:
                'Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji',
              colors: {
                dark: ['#c1cbd5', '#96a3af', '#6c7a88', '#49525b', '#262a2e', '#262a2e', '#202325', '#1a1b1d', '#131415', '#0d0d0d']
              },
              components: {
                Input: {
                  classNames: {
                    input: 'input-base'
                  }
                },
                Card: {
                  classNames: { root: 'card' }
                },
                Divider: {
                  defaultProps: {
                    color: 'var(--border-color)'
                  }
                },
                Modal: {
                  styles: (theme) => ({
                    header: {
                      borderBottom: `1px solid ${theme.colors.dark[5]}`
                    },
                    body: {
                      paddingTop: theme.spacing.md,
                      paddingBottom: theme.spacing.md
                    }
                  })
                }
              }
            }}>
            <Notifications />
            <DatesProvider settings={{}}>
              <Main />
            </DatesProvider>
          </MantineProvider>
        </AuthenticationProvider>
      </QueryClientProvider>
    </GoogleOAuthProvider>
  </StrictMode>
);
