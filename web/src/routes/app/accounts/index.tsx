import { Alert, Button, Skeleton } from '@mantine/core';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { createFileRoute, Navigate } from '@tanstack/react-router';
import { $api } from '@/api/client';
import { AccountEmptyState } from '@/features/account';
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
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: {
        includeArchived: true
      }
    }
  });

  const accounts = accountsQuery.data ?? [];
  const activeAccounts = accounts.filter((a) => !a.archived);

  if (accountsQuery.isLoading || (accounts.length === 0 && accountsQuery.isFetching)) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxWidth: 480, marginInline: 'auto' }}>
        <Skeleton height={58} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={80} radius="md" />
      </div>
    );
  }

  if (accountsQuery.isError) {
    return (
      <div style={{ maxWidth: 480, marginInline: 'auto' }}>
        <Alert icon={<WarningCircleIcon size={20} />} title="Could not load accounts" color="red" variant="light">
          We encountered an issue fetching your accounts. Please check your connection.
          <Button size="xs" variant="outline" color="red" mt="xs" style={{ minHeight: 44 }} onClick={() => accountsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      </div>
    );
  }

  const targetAccount = activeAccounts[0] ?? accounts[0];
  if (!targetAccount) {
    return (
      <div style={{ maxWidth: 480, marginInline: 'auto' }}>
        <AccountEmptyState />
      </div>
    );
  }

  return <Navigate to="/app/accounts/$accountId" params={{ accountId: targetAccount.id }} replace />;
}
