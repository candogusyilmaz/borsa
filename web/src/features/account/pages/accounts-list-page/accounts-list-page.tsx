import { Alert, Badge, Button, Group, Skeleton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { BankIcon, PlusIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useNavigate } from '@tanstack/react-router';
import { useEffect, useRef, useState } from 'react';
import { $api } from '@/api/client';
import { queryClient } from '@/app/query-client';
import { AccountActionsDrawer } from '../../components/account-actions-drawer';
import { AccountActivitiesDrawer } from '../../components/account-activities-drawer';
import { AccountCarousel } from '../../components/account-carousel';
import { BalanceLiquidityCard, RecentActivityCard, StartingBalanceCoverageCard } from '../../components/account-detail-stack';
import { AccountInfoDrawer } from '../../components/account-info-drawer';
import { AccountPickerDrawer } from '../../components/account-picker-drawer';
import { AccountQuickActions } from '../../components/account-quick-actions';
import { AccountSettingsModal } from '../../components/account-settings-modal/account-settings-modal';
import { AccountsListSection } from '../../components/accounts-list-section';
import { ArchiveAccountModal } from '../../components/archive-account-modal/archive-account-modal';
import { CreateAccountModal } from '../../components/create-account-modal/create-account-modal';
import { OpeningCorrectionModal } from '../../components/opening-correction-modal/opening-correction-modal';
import { RecordCashActivityModal } from '../../components/record-cash-activity-modal/record-cash-activity-modal';
import { TransferModal } from '../../components/transfer-modal/transfer-modal';
import classes from './accounts-list-page.module.css';

interface AccountsListPageProps {
  initialAccountId?: string;
}

export function AccountsListPage({ initialAccountId }: AccountsListPageProps) {
  const navigate = useNavigate();
  const heroRef = useRef<HTMLDivElement>(null);

  // Authoritative Query: fetch all accounts once, include archived for switching
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: {
        includeArchived: true
      }
    }
  });

  const rawAccounts = accountsQuery.data ?? [];
  const activeAccounts = rawAccounts.filter((a) => !a.archived);

  // Selected account state: initialize from URL prop or first active account
  const [selectedAccountIdState, setSelectedAccountIdState] = useState<string | null>(initialAccountId ?? null);

  // Sync state if URL query param changes externally (e.g. browser back/forward)
  useEffect(() => {
    if (initialAccountId && initialAccountId !== selectedAccountIdState) {
      setSelectedAccountIdState(initialAccountId);
      setBalanceExpanded(false);
      setStartingExpanded(false);
      setActivityExpanded(false);
    }
  }, [initialAccountId, selectedAccountIdState]);

  // Derive active selected account
  const selectedAccount =
    rawAccounts.find((a) => a.id === selectedAccountIdState) ??
    (activeAccounts.length > 0 ? activeAccounts[0] : rawAccounts.length > 0 ? rawAccounts[0] : null);

  const selectedAccountId = selectedAccount?.id ?? '';

  // Derive carousel slides: include selected account if it is archived, otherwise all active accounts
  const carouselAccounts =
    selectedAccount?.archived && !activeAccounts.some((a) => a.id === selectedAccount.id)
      ? [selectedAccount, ...activeAccounts]
      : activeAccounts.length > 0
        ? activeAccounts
        : rawAccounts;

  // Accordion stack expansion state (independent cards, default all collapsed)
  const [balanceExpanded, setBalanceExpanded] = useState(false);
  const [startingExpanded, setStartingExpanded] = useState(false);
  const [activityExpanded, setActivityExpanded] = useState(false);

  // Modals and Drawers disclosures
  const [createModalOpened, { open: openCreateModal, close: closeCreateModal }] = useDisclosure(false);
  const [transferModalOpened, { open: openTransferModal, close: closeTransferModal }] = useDisclosure(false);
  const [depositModalOpened, { open: openDepositModal, close: closeDepositModal }] = useDisclosure(false);
  const [withdrawModalOpened, { open: openWithdrawModal, close: closeWithdrawModal }] = useDisclosure(false);
  const [settingsModalOpened, { open: openSettingsModal, close: closeSettingsModal }] = useDisclosure(false);
  const [archiveModalOpened, { open: openArchiveModal, close: closeArchiveModal }] = useDisclosure(false);
  const [openingCorrectionOpened, { open: openOpeningCorrection, close: closeOpeningCorrection }] = useDisclosure(false);
  const [actionsDrawerOpened, { open: openActionsDrawer, close: closeActionsDrawer }] = useDisclosure(false);
  const [infoDrawerOpened, { open: openInfoDrawer, close: closeInfoDrawer }] = useDisclosure(false);
  const [pickerDrawerOpened, { open: openPickerDrawer, close: closePickerDrawer }] = useDisclosure(false);
  const [activitiesDrawerOpened, { open: openActivitiesDrawer, close: closeActivitiesDrawer }] = useDisclosure(false);

  // Select account handler: resets accordion sections and updates URL without adding history spam
  function handleSelectAccount(accountId: string) {
    if (accountId === selectedAccountId) return;

    setSelectedAccountIdState(accountId);
    setBalanceExpanded(false);
    setStartingExpanded(false);
    setActivityExpanded(false);

    // Sync with URL query parameter using replace
    navigate({
      to: '/app/accounts',
      search: { account: accountId },
      replace: true
    });
  }

  // Refetch data for selected account
  async function handleRefreshSelectedAccount() {
    if (!selectedAccountId) return;
    queryClient.invalidateQueries({
      predicate: (query) => {
        const key = JSON.stringify(query.queryKey);
        return key.includes(selectedAccountId);
      }
    });
    await accountsQuery.refetch();
  }

  return (
    <section className={classes.container} aria-labelledby="accounts-page-title">
      {/* 1. Header Row */}
      <div className={classes.headerRow}>
        <div className={classes.headerText}>
          <div className={classes.titleArea}>
            <h1 id="accounts-page-title" className={classes.pageTitle}>
              Financial Accounts
            </h1>
            {activeAccounts.length > 0 && (
              <Badge color="brand" variant="light" size="sm" className={classes.desktopBadge}>
                {activeAccounts.length} Active
              </Badge>
            )}
          </div>
          <p className={classes.pageSubtitle}>Manage your cash ledgers and portfolios.</p>
        </div>

        <div className={classes.headerActions}>
          <button type="button" className={classes.createBtn} onClick={openCreateModal} aria-label="Create new financial account">
            <PlusIcon size={20} weight="bold" />
          </button>
        </div>
      </div>

      {/* 2. Loading / Error States */}
      {accountsQuery.isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Skeleton height={200} radius="lg" />
          <Skeleton height={58} radius="md" />
          <Skeleton height={80} radius="md" />
          <Skeleton height={80} radius="md" />
        </div>
      )}

      {accountsQuery.isError && (
        <Alert icon={<WarningCircleIcon size={20} />} title="Could not load accounts" color="red" variant="light">
          We encountered an issue fetching your accounts. Please check your connection.
          <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => accountsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      )}

      {/* 3. Empty State Handling */}
      {!accountsQuery.isLoading && !accountsQuery.isError && rawAccounts.length === 0 && (
        <div className={classes.emptyState}>
          <div className={classes.emptyIcon} aria-hidden="true">
            <BankIcon size={28} weight="light" />
          </div>
          <h3 className={classes.emptyTitle}>No accounts yet</h3>
          <p className={classes.emptyText}>Create your first account to start tracking cash, portfolios, or liabilities.</p>
          <Button color="brand" size="md" leftSection={<PlusIcon size={18} weight="bold" />} onClick={openCreateModal}>
            Create Account
          </Button>
        </div>
      )}

      {!accountsQuery.isLoading &&
        !accountsQuery.isError &&
        rawAccounts.length > 0 &&
        activeAccounts.length === 0 &&
        !selectedAccountIdState && (
          <div className={classes.emptyState}>
            <div className={classes.emptyIcon} aria-hidden="true">
              <BankIcon size={28} weight="light" />
            </div>
            <h3 className={classes.emptyTitle}>All accounts are archived</h3>
            <p className={classes.emptyText}>
              You have {rawAccounts.length} archived account{rawAccounts.length === 1 ? '' : 's'}. You can view archived accounts or create
              a new active account.
            </p>
            <Group gap="sm" mt="xs">
              <Button variant="default" size="sm" onClick={openPickerDrawer}>
                View Archived Accounts
              </Button>
              <Button color="brand" size="sm" leftSection={<PlusIcon size={16} weight="bold" />} onClick={openCreateModal}>
                Create Account
              </Button>
            </Group>
          </div>
        )}

      {/* 4. Main Account-Centric Flow */}
      {!accountsQuery.isLoading && !accountsQuery.isError && (activeAccounts.length > 0 || selectedAccount) && selectedAccount && (
        <div className={classes.mainContentFlow}>
          {/* A. Account Carousel Hero */}
          <div ref={heroRef} className={classes.heroRegion}>
            <AccountCarousel accounts={carouselAccounts} selectedAccountId={selectedAccountId} onSelectAccount={handleSelectAccount} />
          </div>

          {/* B. Quick Actions */}
          <AccountQuickActions
            account={selectedAccount}
            onDeposit={openDepositModal}
            onWithdraw={openWithdrawModal}
            onTransfer={openTransferModal}
            onMore={openActionsDrawer}
          />

          {/* C. Collapsible Detail Cards Stack */}
          <div className={classes.stackSection}>
            {/* Card 1: Balance & Liquidity */}
            <BalanceLiquidityCard
              account={selectedAccount}
              expanded={balanceExpanded}
              onToggle={() => setBalanceExpanded((prev) => !prev)}
            />

            {/* Card 2: Starting Balance & Coverage */}
            <StartingBalanceCoverageCard
              account={selectedAccount}
              expanded={startingExpanded}
              onToggle={() => setStartingExpanded((prev) => !prev)}
              onOpenCorrection={openOpeningCorrection}
            />

            {/* Card 3: Recent Activity */}
            <RecentActivityCard
              account={selectedAccount}
              expanded={activityExpanded}
              onToggle={() => setActivityExpanded((prev) => !prev)}
              onViewAll={openActivitiesDrawer}
            />
          </div>

          {/* D. Single-line View all accounts action */}
          <AccountsListSection accounts={rawAccounts} onOpenAccountPicker={openPickerDrawer} />
        </div>
      )}

      {/* 5. Modals & Drawers */}
      {/* Create Account Modal */}
      <CreateAccountModal opened={createModalOpened} onClose={closeCreateModal} />

      {/* Transfer Modal */}
      {selectedAccount && (
        <TransferModal
          key={`transfer-${selectedAccountId}`}
          opened={transferModalOpened}
          onClose={closeTransferModal}
          defaultSourceAccountId={selectedAccountId}
          onSuccess={handleRefreshSelectedAccount}
        />
      )}

      {/* Deposit Cash Modal */}
      {selectedAccount && (
        <RecordCashActivityModal
          key={`deposit-${selectedAccountId}`}
          account={selectedAccount}
          opened={depositModalOpened}
          onClose={closeDepositModal}
          defaultType="CASH_DEPOSIT"
          onSuccess={handleRefreshSelectedAccount}
        />
      )}

      {/* Withdraw Cash Modal */}
      {selectedAccount && (
        <RecordCashActivityModal
          key={`withdraw-${selectedAccountId}`}
          account={selectedAccount}
          opened={withdrawModalOpened}
          onClose={closeWithdrawModal}
          defaultType="CASH_WITHDRAWAL"
          onSuccess={handleRefreshSelectedAccount}
        />
      )}

      {/* Account Actions Drawer (More) */}
      {selectedAccount && (
        <AccountActionsDrawer
          account={selectedAccount}
          opened={actionsDrawerOpened}
          onClose={closeActionsDrawer}
          onRefresh={handleRefreshSelectedAccount}
          onOpenSettings={openSettingsModal}
          onOpenInfo={openInfoDrawer}
          onOpenArchive={openArchiveModal}
        />
      )}

      {/* Account Specifications & Invariants Drawer */}
      {selectedAccount && <AccountInfoDrawer account={selectedAccount} opened={infoDrawerOpened} onClose={closeInfoDrawer} />}

      {/* Account Picker / Browser Drawer */}
      <AccountPickerDrawer
        accounts={rawAccounts}
        selectedAccountId={selectedAccountId}
        opened={pickerDrawerOpened}
        onClose={closePickerDrawer}
        onSelectAccount={handleSelectAccount}
      />

      {/* Account Settings Modal */}
      {selectedAccount && (
        <AccountSettingsModal
          key={`settings-${selectedAccountId}-${selectedAccount.version ?? 0}`}
          account={selectedAccount}
          opened={settingsModalOpened}
          onClose={closeSettingsModal}
          onRefetchAccount={handleRefreshSelectedAccount}
        />
      )}

      {/* Archive Account Confirmation Modal */}
      {selectedAccount && (
        <ArchiveAccountModal
          key={`archive-${selectedAccountId}-${selectedAccount.version ?? 0}`}
          account={selectedAccount}
          opened={archiveModalOpened}
          onClose={closeArchiveModal}
          onRefetchAccount={async () => {
            await accountsQuery.refetch();
            // Automatically switch to first remaining active account or reset to null
            const remaining = activeAccounts.filter((a) => a.id !== selectedAccountId);
            const firstRemaining = remaining[0];
            if (firstRemaining) {
              handleSelectAccount(firstRemaining.id);
            } else {
              setSelectedAccountIdState(null);
              navigate({
                to: '/app/accounts',
                search: {},
                replace: true
              });
            }
          }}
        />
      )}

      {/* Opening Balance Correction Modal */}
      {selectedAccount && (
        <OpeningCorrectionModal
          key={`opening-correction-${selectedAccountId}-${selectedAccount.version ?? 0}`}
          account={selectedAccount}
          opened={openingCorrectionOpened}
          onClose={closeOpeningCorrection}
          onRefetchAccount={handleRefreshSelectedAccount}
        />
      )}

      {/* Full Activity History Drawer */}
      {selectedAccount && (
        <AccountActivitiesDrawer
          account={selectedAccount}
          opened={activitiesDrawerOpened}
          onClose={closeActivitiesDrawer}
          onOpenDeposit={openDepositModal}
          onOpenWithdraw={openWithdrawModal}
          onOpenTransfer={openTransferModal}
          onActivityUpdated={handleRefreshSelectedAccount}
        />
      )}
    </section>
  );
}
