import { createFileRoute } from '@tanstack/react-router';
import { AccountsListPage } from '@/features/account';
import { createSeoMeta } from '@/shared/utils/seo';

interface AccountsSearch {
  account?: string;
}

export const Route = createFileRoute('/app/accounts/')({
  validateSearch: (search: Record<string, unknown>): AccountsSearch => {
    return {
      account: typeof search.account === 'string' ? search.account : undefined
    };
  },
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
  component: AccountsRouteComponent
});

function AccountsRouteComponent() {
  const { account } = Route.useSearch();
  return <AccountsListPage initialAccountId={account} />;
}
