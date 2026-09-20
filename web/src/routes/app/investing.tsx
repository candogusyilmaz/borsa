import { createFileRoute } from '@tanstack/react-router';
import { InvestingPage } from '@/features/investing';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/investing')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Investing & Portfolio Positions',
      path: '/app/investing',
      noIndex: true
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: InvestingRouteComponent
});

function InvestingRouteComponent() {
  return <InvestingPage />;
}
