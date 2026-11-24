import { createRouter, RouterProvider } from '@tanstack/react-router';
import { useEffect, useRef } from 'react';
import { useAuthentication } from '~/features/auth/authentication-context';
import { routeTree } from '~/routeTree.gen';
import { ErrorView } from '~/shared/components/error-view/error-view';
import { queryClient } from './query-client';

const router = createRouter({
  routeTree,
  defaultErrorComponent: ({ error, reset }) => <ErrorView error={error} reset={reset} variant="page" />,
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
  const prevAuthenticated = useRef(auth.isAuthenticated);

  useEffect(() => {
    if (prevAuthenticated.current === auth.isAuthenticated) return;
    prevAuthenticated.current = auth.isAuthenticated;
    void router.invalidate();
  }, [auth.isAuthenticated]);

  return <RouterProvider router={router} context={{ auth, queryClient }} />;
}
