import { Alert, Button, Skeleton, Text } from '@mantine/core';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import {
  AccountDetailContent,
  AccountDetailOverlayHost,
  AccountDetailOverlayProvider,
  AccountEmptyState,
  useAccountsLayout
} from '@/features/account';
import type { FinancialAccount } from '@/features/account/types';
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
  const navigate = useNavigate();
  const { accounts, activeAccounts, isLoading, isFetching, isError, openCreateModal, openPickerDrawer, refetchAccounts } =
    useAccountsLayout();

  const account = accounts.find((a) => a.id === accountId);

  // Render loading skeleton during initial fetch or when refetching in background for a missing account
  if (isLoading || (!account && isFetching)) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <Skeleton height={58} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={80} radius="md" />
      </div>
    );
  }

  // If layout encountered an API error, suppress child alerts (the layout displays the retry banner)
  if (isError) {
    return null;
  }

  // If no accounts exist in the workspace, render the empty state
  if (accounts.length === 0) {
    return <AccountEmptyState onOpenCreate={openCreateModal} />;
  }

  // If specific account was not found
  if (!account) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Account Not Found" color="red" variant="light">
        <Text size="sm" mb="xs">
          Could not find the requested financial account. It may have been archived, deleted, or does not exist.
        </Text>
        <Button
          size="sm"
          variant="outline"
          color="red"
          style={{ minHeight: 44 }}
          onClick={() => {
            const firstActive = activeAccounts[0] ?? accounts[0];
            if (firstActive) {
              navigate({ to: '/app/accounts/$accountId', params: { accountId: firstActive.id }, replace: true });
            } else {
              navigate({ to: '/app/accounts', replace: true });
            }
          }}>
          Go to Available Account
        </Button>
      </Alert>
    );
  }

  const selectedAccount = account;

  async function handleAccountArchived() {
    const res = await refetchAccounts();
    const updatedAccounts = (res as { data?: FinancialAccount[] })?.data ?? accounts;
    const remainingActive = updatedAccounts.filter((a) => !a.archived && a.id !== selectedAccount.id);
    const firstRemaining = remainingActive[0] ?? updatedAccounts.find((a) => a.id !== selectedAccount.id);
    if (firstRemaining) {
      navigate({ to: '/app/accounts/$accountId', params: { accountId: firstRemaining.id }, replace: true });
    } else {
      navigate({ to: '/app/accounts', replace: true });
    }
  }

  return (
    <AccountDetailOverlayProvider key={selectedAccount.id}>
      <AccountDetailContent account={selectedAccount} allAccounts={accounts} onOpenAccountPicker={openPickerDrawer} />
      <AccountDetailOverlayHost account={selectedAccount} refetchAccounts={refetchAccounts} onAccountArchived={handleAccountArchived} />
    </AccountDetailOverlayProvider>
  );
}
