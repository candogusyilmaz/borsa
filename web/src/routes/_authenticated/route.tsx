import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { resolveSession } from '@/api/session';
import { AppShell } from '@/shared/components/app-shell';

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async ({ context, location }) => {
    const session = await resolveSession(context.queryClient);

    if (session.status === 'anonymous') {
      throw redirect({
        to: '/login',
        search: {
          redirect: location.href
        },
        replace: true
      });
    }

    return {
      user: session.user
    };
  },
  pendingComponent: () => (
    <Center h="100vh">
      <Loader size="lg" />
    </Center>
  ),
  component: AuthenticatedLayout
});

function AuthenticatedLayout() {
  const { user } = Route.useRouteContext();

  return (
    <AppShell user={user}>
      <Outlet />
    </AppShell>
  );
}
