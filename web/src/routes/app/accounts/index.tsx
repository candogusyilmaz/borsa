import { Skeleton } from '@mantine/core';
import { createFileRoute, Navigate } from '@tanstack/react-router';
import { AccountEmptyState, useAccountsLayout } from '@/features/account';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/accounts/')({
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
  component: AccountsIndexRouteComponent
});

function AccountsIndexRouteComponent() {
  const { accounts, activeAccounts, isLoading, isFetching, isError, openCreateModal } = useAccountsLayout();

  if (isLoading || (accounts.length === 0 && isFetching)) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <Skeleton height={58} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={80} radius="md" />
      </div>
    );
  }

  if (isError) {
    return null;
  }

  if (accounts.length === 0) {
    return <AccountEmptyState onOpenCreate={openCreateModal} />;
  }

  const targetAccount = activeAccounts[0] ?? accounts[0];
  if (!targetAccount) {
    return <AccountEmptyState onOpenCreate={openCreateModal} />;
  }

  return <Navigate to="/app/accounts/$accountId" params={{ accountId: targetAccount.id }} replace />;
}
