import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { AuthenticatedOverlays } from '~/app/authenticated-overlays';

export const Route = createFileRoute('/_authenticated')({
  component: RouteComponent,
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated) {
      throw redirect({ to: '/login' });
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
