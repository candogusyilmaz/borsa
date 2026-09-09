import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/client';
import { AppShell } from '@/shared/components/app-shell';

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: ({ context, location }) => {
    const isAuthed = Boolean(context.auth?.isAuthenticated || getAccessToken());
    if (!isAuthed) {
      throw redirect({
        to: '/login',
        search: {
          redirect: location.href
        },
        replace: true
      });
    }
  },
  pendingComponent: () => (
    <Center h="100vh">
      <Loader size="lg" />
    </Center>
  ),
  component: AuthenticatedLayout
});

function AuthenticatedLayout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}
