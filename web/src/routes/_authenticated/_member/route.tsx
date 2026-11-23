import { createFileRoute, Outlet, redirect } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { MemberShell } from '~/app/shell/member-shell';

export const Route = createFileRoute('/_authenticated/_member')({
  component: RouteComponent,
  beforeLoad: async ({ context: { queryClient } }) => {
    const onboardingCompleted = await queryClient.ensureQueryData($api.queryOptions('get', '/api/onboarding/status'));

    if (!onboardingCompleted) {
      throw redirect({ to: '/onboarding' });
    }
  }
});

function RouteComponent() {
  return (
    <MemberShell>
      <Outlet />
    </MemberShell>
  );
}
