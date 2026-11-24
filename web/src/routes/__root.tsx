import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, HeadContent, Outlet } from '@tanstack/react-router';
import { DocumentTitle } from '~/app/document-title';
import type { AuthContext } from '~/features/auth/authentication-context.tsx';
import { ErrorView } from '~/shared/components/error-view/error-view';
import { NotFound } from '~/shared/components/not-found/not-found';

export const Route = createRootRouteWithContext<{
  queryClient: QueryClient;
  auth: AuthContext;
}>()({
  component: RootComponent,
  notFoundComponent: () => <NotFound />,
  errorComponent: ({ error, reset }) => (
    <ErrorView error={error} reset={reset} variant="page" />
  ),
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
