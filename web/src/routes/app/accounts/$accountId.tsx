import { createFileRoute } from '@tanstack/react-router';
import { AccountDetailPage } from '@/features/account';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/accounts/$accountId')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Financial Account Detail',
      noIndex: true
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: AccountDetailRouteComponent
});

function AccountDetailRouteComponent() {
  const { accountId } = Route.useParams();
  return <AccountDetailPage accountId={accountId} />;
}
