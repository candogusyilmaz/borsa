import { createRouter, RouterProvider } from '@tanstack/react-router';
import { useAuthentication } from '~/features/auth/authentication-context';
import { routeTree } from '~/routeTree.gen';
import { NetworkError } from '~/shared/components/network-error/network-error';
import { queryClient } from './query-client';

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

export function AppRouter() {
  const auth = useAuthentication();
  return <RouterProvider router={router} context={{ auth, queryClient }} />;
}
