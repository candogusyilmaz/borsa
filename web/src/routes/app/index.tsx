import { createFileRoute } from '@tanstack/react-router';
import { DashboardPage } from '@/features/dashboard';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Dashboard',
      path: '/app',
      noIndex: true
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: DashboardRouteComponent
});

function DashboardRouteComponent() {
  const { user } = Route.useRouteContext();
  return <DashboardPage user={user} />;
}
