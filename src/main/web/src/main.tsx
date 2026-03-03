import { ThemeProvider } from '~/lib/shadcn/core';
import { DatesProvider } from '~/lib/shadcn/dates';
import { Notifications } from '~/lib/shadcn/notifications';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRouter, RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthenticationProvider, useAuthentication } from '~/lib/AuthenticationContext.tsx';
import { AxiosProvider } from '~/lib/axios.ts';
import { routeTree } from '~/routeTree.gen';

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
          <ThemeProvider
            defaultColorScheme="dark"
            >
            <Notifications />
            <DatesProvider settings={{}}>
              <Main />
            </DatesProvider>
          </ThemeProvider>
        </AuthenticationProvider>
      </QueryClientProvider>
    </GoogleOAuthProvider>
  </StrictMode>
);
