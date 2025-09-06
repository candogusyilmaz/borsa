import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { queries } from '~/api';
import { GlobalModals } from '~/components/GlobalModals';

export const Route = createFileRoute('/_authenticated')({
  component: RouteComponent,
  beforeLoad: async (p) => {
    if (!p.context.auth.isAuthenticated) throw redirect({ to: '/login' });

    await p.context.queryClient.ensureQueryData(queries.dashboard.getAllDashboards());
  }
});

function RouteComponent() {
  return (
    <>
      <GlobalModals />
      <Outlet />
    </>
  );
}
