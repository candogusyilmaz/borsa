import { createRouter } from '@tanstack/react-router';
import { queryClient } from '@/app/query-client';
import { routeTree } from '@/routeTree.gen';

export const router = createRouter({
  routeTree,
  context: {
    queryClient
  },
  defaultPreload: false,
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
