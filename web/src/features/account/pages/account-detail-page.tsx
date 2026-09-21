import { Alert, Button, Skeleton, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { useNavigate } from '@tanstack/react-router';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { AccountActivitiesOverlay } from '../components/account-activities/account-activities';
import { AccountCarousel } from '../components/account-carousel';
import {
  AccountPositionsCard,
  BalanceLiquidityCard,
  RecentActivityCard,
  StartingBalanceCoverageCard
} from '../components/account-detail-stack';
import { AccountEmptyState } from '../components/account-empty-state';
import { AccountLayoutHeader } from '../components/account-layout-header';
import { AccountPickerOverlay } from '../components/account-picker/account-picker';
import { AccountQuickActions } from '../components/account-quick-actions';
import { AccountsListSection } from '../components/accounts-list-section';
import { CreateAccountOverlay } from '../components/create-account/create-account';
import { OpeningCorrectionOverlay } from '../components/opening-correction/opening-correction';
import type { FinancialAccount } from '../types';
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

  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: {
        includeArchived: true
      }
    }
  });

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const rawAccounts = accountsQuery.data ?? [];
  const activeAccounts = rawAccounts.filter((a) => !a.archived);
  const account = accountQuery.data;

  const selectedInList = rawAccounts.find((a) => a.id === accountId);
  const selectedAccount = account ?? selectedInList;
  const isArchived = selectedAccount?.archived ?? false;

  const carouselAccounts =
    activeAccounts.length > 0
      ? isArchived && selectedAccount && !activeAccounts.some((a) => a.id === selectedAccount.id)
        ? [selectedAccount, ...activeAccounts]
        : activeAccounts
      : rawAccounts;

  function handleSelectAccount(targetId: string) {
    if (targetId === accountId) return;
    navigate({
      to: '/app/accounts/$accountId',
      params: { accountId: targetId },
      replace: true
    });
  }

  function openCreateAccount() {
    const handle = CreateAccountOverlay.open();
    handle.closed.then((outcome) => {
      if (outcome.status === 'completed') {
        navigate({
          to: '/app/accounts/$accountId',
          params: { accountId: outcome.value.id },
          replace: !accountId
        });
      }
    });
  }

  function openAccountPicker() {
    const handle = AccountPickerOverlay.open({ selectedAccountId: accountId });
    handle.closed.then((outcome) => {
      if (outcome.status === 'completed' && outcome.value !== accountId) {
        navigate({
          to: '/app/accounts/$accountId',
          params: { accountId: outcome.value },
          replace: true
        });
      }
    });
  }

  async function handleAccountArchived() {
    const result = await accountsQuery.refetch();
    const updatedAccounts = result.data ?? rawAccounts;
    const remainingActive = updatedAccounts.filter((a) => !a.archived && a.id !== accountId);
    const firstRemaining = remainingActive[0] ?? updatedAccounts.find((a) => a.id !== accountId);
    if (firstRemaining) {
      navigate({ to: '/app/accounts/$accountId', params: { accountId: firstRemaining.id }, replace: true });
    } else {
      navigate({ to: '/app/accounts', replace: true });
    }
  }

  function renderDetailContent() {
    if (accountsQuery.isError) {
      return null;
    }

    if (!accountsQuery.isLoading && rawAccounts.length === 0) {
      return <AccountEmptyState onOpenCreate={openCreateAccount} />;
    }

    if (accountQuery.isPending || (!account && accountQuery.isFetching)) {
      return (
        <div className={classes.detailContentFlow}>
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
              const firstActive = activeAccounts[0] ?? rawAccounts[0];
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

    return (
      <div className={classes.detailContentFlow}>
        <AccountQuickActions account={account} onAccountArchived={handleAccountArchived} />
        <AccountDetailStack account={account} />
        <AccountsListSection accounts={rawAccounts} onOpenAccountPicker={openAccountPicker} />
      </div>
    );
  }

  return (
    <section className={classes.container} aria-labelledby="accounts-page-title">
      <AccountLayoutHeader activeCount={activeAccounts.length} onOpenCreate={openCreateAccount} />

      {accountsQuery.isLoading && (
        <div className={classes.heroRegion}>
          <Skeleton height={200} radius="lg" />
        </div>
      )}

      {accountsQuery.isError && (
        <Alert icon={<WarningCircleIcon size={20} />} title="Could not load accounts" color="red" variant="light">
          We encountered an issue fetching your accounts. Please check your connection.
          <Button size="xs" variant="outline" color="red" mt="xs" style={{ minHeight: 44 }} onClick={() => accountsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      )}

      {!accountsQuery.isLoading && !accountsQuery.isError && rawAccounts.length > 0 && (
        <div className={classes.heroRegion}>
          <AccountCarousel accounts={carouselAccounts} selectedAccountId={accountId} onSelectAccount={handleSelectAccount} />
        </div>
      )}

      {renderDetailContent()}
    </section>
  );
}
