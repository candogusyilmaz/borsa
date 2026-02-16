import { type DefaultMantineColor, type MantineColorsTuple, MantineProvider } from '@mantine/core';
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

type ExtendedCustomColors = 'slate' | 'accent' | DefaultMantineColor;

declare module '@mantine/core' {
  export interface MantineThemeColorsOverride {
    colors: Record<ExtendedCustomColors, MantineColorsTuple>;
  }
}

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
                dark: ['#c1cbd5', '#96a3af', '#6c7a88', '#49525b', '#262a2e', '#262a2e', '#202325', '#1a1b1d', '#131415', '#0d0d0d'],
                accent: ['#ebecff', '#d3d4ff', '#a3a5f8', '#6366f1', '#474aed', '#2d2feb', '#1d22eb', '#1016d1', '#0713bc', '#000ea6'],
                slate: [
                  'oklch(98.4% 0.003 247.858)',
                  'oklch(96.8% 0.007 247.896)',
                  'oklch(92.9% 0.013 255.508)',
                  'oklch(86.9% 0.022 252.894)',
                  'oklch(70.4% 0.04 256.788)',
                  'oklch(55.4% 0.046 257.417)',
                  'oklch(44.6% 0.043 257.281)',
                  'oklch(37.2% 0.044 257.287)',
                  'oklch(27.9% 0.041 260.031)',
                  'oklch(20.8% 0.042 265.755)',
                  'oklch(12.9% 0.042 264.695)'
                ]
              },
              primaryColor: 'accent',
              primaryShade: 4,
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
                  defaultProps: {
                    radius: 'xl'
                  },
                  classNames: {
                    content: 'modal-content'
                  },
                  styles: {
                    header: {
                      background: 'transparent'
                    }
                  }
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
