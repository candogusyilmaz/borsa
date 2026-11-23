import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { AuthenticatedOverlays } from '~/app/authenticated-overlays';
import { useAutoRefreshToken } from '~/features/auth/use-auto-refresh-token';

export const Route = createFileRoute('/_authenticated')({
  component: RouteComponent,
  beforeLoad: async (p) => {
    if (!p.context.auth.isAuthenticated) throw redirect({ to: '/login' });
  },
  pendingComponent: () => (
    <Center h="100dvh">
      <Loader />
    </Center>
  )
});

function RouteComponent() {
  useAutoRefreshToken();

  return (
    <>
      <AuthenticatedOverlays />
      <Outlet />
    </>
  );
}
