import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { GlobalModals } from '~/components/GlobalModals';
import { useAutoRefreshToken } from '~/hooks/use-auto-refresh-token';
export const Route = createFileRoute('/_authenticated')({
  component: RouteComponent,
  beforeLoad: async (p) => {
    if (!p.context.auth.isAuthenticated) throw redirect({ to: '/login' });
  },
  pendingComponent: () => <div>Loading...</div>
});

function RouteComponent() {
  useAutoRefreshToken();

  return (
    <>
      <GlobalModals />
      <Outlet />
    </>
  );
}
