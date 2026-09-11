import { Center, Loader } from '@mantine/core';
import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { resolveSession } from '@/api/session';
import { AppShell } from '@/shared/components/app-shell';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app')({
  head: () => {
    const seo = createSeoMeta({
      noIndex: true,
      canonical: false
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
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
