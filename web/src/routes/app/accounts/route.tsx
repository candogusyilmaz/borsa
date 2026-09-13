import { createFileRoute } from '@tanstack/react-router';
import { AccountsLayout } from '@/features/account';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/accounts')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Financial Accounts',
      path: '/app/accounts',
      noIndex: true
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: AccountsLayoutRouteComponent
});

function AccountsLayoutRouteComponent() {
  return <AccountsLayout />;
}
