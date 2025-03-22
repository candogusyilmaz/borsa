import type { QueryClient } from '@tanstack/react-query';
import { DefaultGlobalNotFound, Outlet, createRootRouteWithContext } from '@tanstack/react-router';
import type { AuthContext } from '~/lib/AuthenticationContext.tsx';

export const Route = createRootRouteWithContext<{
  queryClient: QueryClient;
  auth: AuthContext;
}>()({
  component: RootComponent,
  notFoundComponent: () => <DefaultGlobalNotFound />
});

function RootComponent() {
  return <Outlet />;
}
