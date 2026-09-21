import { Alert, Button, Skeleton, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { useNavigate } from '@tanstack/react-router';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { AccountActivitiesOverlay } from '../components/account-activities/account-activities';
import {
  AccountPositionsCard,
  BalanceLiquidityCard,
  RecentActivityCard,
  StartingBalanceCoverageCard
} from '../components/account-detail-stack';
import { AccountEmptyState } from '../components/account-empty-state';
import { AccountQuickActions } from '../components/account-quick-actions';
import { AccountsListSection } from '../components/accounts-list-section';
import { OpeningCorrectionOverlay } from '../components/opening-correction/opening-correction';
import type { FinancialAccount } from '../types';
import { useAccountWorkspace } from '../workspace';
import classes from './account-detail-page.module.css';

interface AccountDetailPageProps {
  accountId: string;
}

function AccountDetailStack({ account }: { account: FinancialAccount }) {
  const [balanceExpanded, { toggle: toggleBalance }] = useDisclosure(false);
  const [positionsExpanded, { toggle: togglePositions }] = useDisclosure(true);
  const [startingExpanded, { toggle: toggleStarting }] = useDisclosure(false);
  const [activityExpanded, { toggle: toggleActivity }] = useDisclosure(false);

  return (
    <div className={classes.stackSection}>
      <BalanceLiquidityCard account={account} expanded={balanceExpanded} onToggle={toggleBalance} />
      {account.kind === 'BROKERAGE' && <AccountPositionsCard account={account} expanded={positionsExpanded} onToggle={togglePositions} />}
      <StartingBalanceCoverageCard
        account={account}
        expanded={startingExpanded}
        onToggle={toggleStarting}
        onOpenCorrection={() => OpeningCorrectionOverlay.open({ accountId: account.id })}
      />
      <RecentActivityCard
        account={account}
        expanded={activityExpanded}
        onToggle={toggleActivity}
        onViewAll={() => AccountActivitiesOverlay.open({ accountId: account.id, accountName: account.name })}
      />
    </div>
  );
}

export function AccountDetailPage({ accountId }: AccountDetailPageProps) {
  const navigate = useNavigate();
  const {
    accounts,
    activeAccounts,
    isLoading: accountsLoading,
    isError: accountsError,
    openCreateAccount,
    openAccountPicker,
    refetchAccounts
  } = useAccountWorkspace();

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const account = accountQuery.data;

  // The URL selects the account; the layout list may still be loading on a direct visit.
  if (accountQuery.isPending || (!account && accountQuery.isFetching)) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <Skeleton height={58} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={80} radius="md" />
      </div>
    );
  }

  if (!account && accountQuery.isError && normalizeError(accountQuery.error).status !== 404) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account" color="red" variant="light">
        <Text size="sm" mb="xs">
          The requested financial account could not be loaded. Please try again.
        </Text>
        <Button size="sm" variant="outline" color="red" style={{ minHeight: 44 }} onClick={() => accountQuery.refetch()}>
          Retry
        </Button>
      </Alert>
    );
  }

  if (!account && accountsLoading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <Skeleton height={58} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={80} radius="md" />
      </div>
    );
  }

  // Preserve the workspace empty state only when the list actually loaded empty.
  if (!account && !accountsError && accounts.length === 0) {
    return <AccountEmptyState onOpenCreate={openCreateAccount} />;
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
    <div className={classes.detailContentFlow}>
      <AccountQuickActions account={selectedAccount} onAccountArchived={handleAccountArchived} />
      <AccountDetailStack account={selectedAccount} />
      <AccountsListSection accounts={accounts} onOpenAccountPicker={openAccountPicker} />
    </div>
  );
}
