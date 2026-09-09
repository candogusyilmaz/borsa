import { createRouter } from '@tanstack/react-router';
import { getAccessToken } from '@/api/client';
import { queryClient } from '@/app/query-client';
import { routeTree } from '@/routeTree.gen';
import type { AuthContextValue } from '@/shared/types/auth';

function getInitialAuthContext(): AuthContextValue {
  const token = getAccessToken();
  return {
    user: null,
    isAuthenticated: Boolean(token),
    isLoading: true,
    login: async () => {},
    logout: async () => {}
  };
}

export const router = createRouter({
  routeTree,
  context: {
    queryClient,
    auth: getInitialAuthContext()
  },
  defaultPreload: 'intent',
  scrollRestoration: true,
  defaultPendingMs: 300,
  defaultPendingMinMs: 400,
  defaultViewTransition: true
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
