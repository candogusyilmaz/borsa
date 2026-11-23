import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, HeadContent, Outlet } from '@tanstack/react-router';
import { DocumentTitle } from '~/app/document-title';
import type { AuthContext } from '~/features/auth/authentication-context.tsx';
import { NotFound } from '~/shared/components/not-found/not-found';

export const Route = createRootRouteWithContext<{
  queryClient: QueryClient;
  auth: AuthContext;
}>()({
  component: RootComponent,
  notFoundComponent: () => <NotFound />
});

function RootComponent() {
  return (
    <>
      <HeadContent />
      <DocumentTitle />
      <Outlet />
    </>
  );
}
