import { createRouter } from '@tanstack/react-router';
import { queryClient } from '@/app/query-client';
import { routeTree } from '@/routeTree.gen';
import type { AuthContextValue } from '@/shared/types/auth';

export const router = createRouter({
  routeTree,
  context: {
    queryClient,
    auth: undefined! as AuthContextValue
  },
  defaultPreload: 'intent',
  scrollRestoration: true
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
