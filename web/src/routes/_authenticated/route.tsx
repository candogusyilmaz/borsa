import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getValidAccessToken } from '~/api/auth-refresh';
import { isTokenExpiringSoon, sessionStore } from '~/api/token';
import { AuthenticatedOverlays } from '~/app/authenticated-overlays';

export const Route = createFileRoute('/_authenticated')({
  component: RouteComponent,
  beforeLoad: async ({ context }) => {
    if (!context.auth.isAuthenticated) {
      throw redirect({ to: '/login' });
    }

    const token = sessionStore.getToken();
    if (token && isTokenExpiringSoon(token)) {
      try {
        await getValidAccessToken();
      } catch {
        if (!sessionStore.getSession()) {
          throw redirect({ to: '/login' });
        }
      }
    }
  },
  pendingComponent: () => (
    <Center h="100dvh">
      <Loader />
    </Center>
  )
});

function RouteComponent() {
  return (
    <>
      <AuthenticatedOverlays />
      <Outlet />
    </>
  );
}
