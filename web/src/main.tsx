import { MantineProvider } from '@mantine/core';
import { DatesProvider } from '@mantine/dates';
import { Notifications } from '@mantine/notifications';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRouter, RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthenticationProvider, useAuthentication } from '~/lib/AuthenticationContext.tsx';
import { routeTree } from '~/routeTree.gen';

import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/charts/styles.css';
import '@mantine/spotlight/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/dropzone/styles.css';

import '~/styles/global.css';
import '~/styles/canverse-mantine-vars.css';

import { NetworkError } from './components/network-error/network-error';
import { cssVariablesResolver, theme } from './styles/theme';

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

  return <RouterProvider router={router} context={{ auth, queryClient }} />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <GoogleOAuthProvider clientId="11207596743-8ql23qmgre6sssabg68hvsrcioucd686.apps.googleusercontent.com">
      <QueryClientProvider client={queryClient}>
        <AuthenticationProvider>
          <MantineProvider cssVariablesResolver={cssVariablesResolver} defaultColorScheme="dark" theme={theme}>
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
