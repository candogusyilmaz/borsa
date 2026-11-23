import { createFileRoute } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { DashboardPage } from '~/features/dashboard/pages/dashboard-page';

export const Route = createFileRoute('/_authenticated/_member/dashboard')({
  component: DashboardPage,
  loader: async ({ context: { queryClient } }) => {
    await queryClient.ensureQueryData($api.queryOptions('get', '/api/dashboards'));
  }
});
