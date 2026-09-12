import { Alert, Badge, Button, Group, Loader, Select, Skeleton, Switch, Text, TextInput, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  ArchiveIcon,
  ArrowClockwiseIcon,
  ArrowsLeftRightIcon,
  BankIcon,
  ChartLineUpIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  WalletIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { AccountCard } from '../../components/account-card/account-card';
import { CreateAccountModal } from '../../components/create-account-modal/create-account-modal';
import { CurrencyBalancesCard } from '../../components/currency-balances-card/currency-balances-card';
import { TransferModal } from '../../components/transfer-modal/transfer-modal';
import classes from './accounts-list-page.module.css';

export function AccountsListPage() {
  const [createModalOpened, { open: openCreateModal, close: closeCreateModal }] = useDisclosure(false);
  const [transferModalOpened, { open: openTransferModal, close: closeTransferModal }] = useDisclosure(false);

  // Filters state
  const [showArchived, setShowArchived] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [modeFilter, setModeFilter] = useState('ALL');
  const [kindFilter, setKindFilter] = useState('ALL');

  // Authoritative API Query: fetch all accounts once, progressively disclose archived
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: {
        includeArchived: true
      }
    }
  });

  const rawAccounts = accountsQuery.data ?? [];

  // Summary statistics computed directly
  const activeAccountsCount = rawAccounts.filter((a) => !a.archived).length;
  const archivedAccountsCount = rawAccounts.filter((a) => a.archived).length;
  const fullLedgerCount = rawAccounts.filter((a) => a.trackingMode === 'FULL_LEDGER' && !a.archived).length;
  const holdingsOnlyCount = rawAccounts.filter((a) => a.trackingMode === 'HOLDINGS_ONLY' && !a.archived).length;

  // Filtered accounts computed directly without useMemo
  const filteredAccounts = rawAccounts.filter((account) => {
    // 1. Search filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      const matchName = account.name.toLowerCase().includes(q);
      const matchCurrency = account.currency.toLowerCase().includes(q);
      if (!matchName && !matchCurrency) return false;
    }

    // 2. Mode filter
    if (modeFilter !== 'ALL' && account.trackingMode !== modeFilter) {
      return false;
    }

    // 3. Kind filter
    if (kindFilter !== 'ALL' && account.kind !== kindFilter) {
      return false;
    }

    // 4. Archive filter
    if (!showArchived && account.archived) {
      return false;
    }

    return true;
  });

  return (
    <section className={classes.container} aria-labelledby="accounts-page-title">
      {/* 1. Header & Primary Action */}
      <div className={classes.headerRow}>
        <div className={classes.headerText}>
          <div className={classes.titleWithBadge}>
            <Title order={1} id="accounts-page-title" className="app-page-title">
              Financial Accounts
            </Title>
            {accountsQuery.data && (
              <Badge color="brand" variant="light" size="md">
                {activeAccountsCount} Active
              </Badge>
            )}
          </div>
          <Text size="sm" c="dimmed">
            Manage your double-entry cash ledgers, brokerage portfolios, and liability lines.
          </Text>
        </div>

        <div className={classes.actionsBar}>
          <Button
            variant="default"
            size="md"
            leftSection={<ArrowsLeftRightIcon size={18} weight="bold" />}
            onClick={openTransferModal}
            aria-label="Transfer funds between accounts">
            Transfer
          </Button>

          <Button
            variant="default"
            size="md"
            leftSection={<ArrowClockwiseIcon size={16} />}
            loading={accountsQuery.isFetching}
            onClick={() => accountsQuery.refetch()}
            aria-label="Refresh accounts list">
            Refresh
          </Button>

          <Button
            color="brand"
            size="md"
            leftSection={<PlusIcon size={18} weight="bold" />}
            onClick={openCreateModal}
            aria-label="Create new account">
            Create Account
          </Button>
        </div>
      </div>

      {/* 2. Overview Stat Cards */}
      <div className={classes.statGrid}>
        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <BankIcon size={22} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              Active Accounts
            </Text>
            <Text size="lg" fw={700}>
              {accountsQuery.isLoading ? <Loader size="xs" /> : activeAccountsCount}
            </Text>
          </div>
        </div>

        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <WalletIcon size={22} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              Full Ledger Mode
            </Text>
            <Text size="lg" fw={700}>
              {accountsQuery.isLoading ? <Loader size="xs" /> : fullLedgerCount}
            </Text>
          </div>
        </div>

        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <ChartLineUpIcon size={22} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              Holdings Only Mode
            </Text>
            <Text size="lg" fw={700}>
              {accountsQuery.isLoading ? <Loader size="xs" /> : holdingsOnlyCount}
            </Text>
          </div>
        </div>

        <div className={classes.statCard}>
          <div className={classes.statIconWrap} aria-hidden="true">
            <ArchiveIcon size={22} weight="duotone" />
          </div>
          <div className={classes.statContent}>
            <Text size="xs" c="dimmed">
              Archived Accounts
            </Text>
            <Text size="lg" fw={700}>
              {accountsQuery.isLoading ? <Loader size="xs" /> : archivedAccountsCount}
            </Text>
          </div>
        </div>
      </div>

      {/* 3. Cash Pockets & Balances by Currency */}
      <CurrencyBalancesCard accounts={rawAccounts} isLoading={accountsQuery.isLoading} />

      {/* 4. Filter & Search Controls */}
      <div className={classes.filterCard}>
        <div className={classes.filterRow}>
          <div className={classes.filterControls}>
            <TextInput
              placeholder="Search by account name or currency..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.currentTarget.value)}
              leftSection={<MagnifyingGlassIcon size={16} />}
              className={classes.searchFilter}
              aria-label="Search accounts"
            />

            <Select
              value={modeFilter}
              onChange={(val) => setModeFilter(val || 'ALL')}
              data={[
                { value: 'ALL', label: 'All Tracking Modes' },
                { value: 'FULL_LEDGER', label: 'Full Ledger Only' },
                { value: 'HOLDINGS_ONLY', label: 'Holdings Only' }
              ]}
              className={classes.selectFilter}
              aria-label="Filter by tracking mode"
            />

            <Select
              value={kindFilter}
              onChange={(val) => setKindFilter(val || 'ALL')}
              data={[
                { value: 'ALL', label: 'All Account Kinds' },
                { value: 'CASH_CURRENT', label: 'Current / Checking' },
                { value: 'CASH_SAVINGS', label: 'Savings' },
                { value: 'CASH_WALLET', label: 'Cash Wallet' },
                { value: 'BROKERAGE', label: 'Brokerage' },
                { value: 'CREDIT_CARD', label: 'Credit Card' },
                { value: 'LOAN', label: 'Loan / Debt' }
              ]}
              className={classes.selectFilter}
              aria-label="Filter by account kind"
            />
          </div>

          {/* Show / Hide Archived Accounts Toggle */}
          <div className={classes.archivedToggleWrap}>
            <Text size="xs" fw={500}>
              Show Archived
            </Text>
            <Switch
              checked={showArchived}
              onChange={(e) => setShowArchived(e.currentTarget.checked)}
              size="sm"
              color="brand"
              aria-label="Toggle show archived accounts"
            />
          </div>
        </div>
      </div>

      {/* 4. Query States & Results */}
      {accountsQuery.isError && (
        <Alert icon={<WarningCircleIcon size={20} />} title="Could not load accounts" color="red" variant="light">
          We encountered an issue fetching your accounts. Please verify your connection.
          <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => accountsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      )}

      {accountsQuery.isLoading && (
        <div className={classes.accountsGrid}>
          <Skeleton height={200} radius="md" />
          <Skeleton height={200} radius="md" />
          <Skeleton height={200} radius="md" />
        </div>
      )}

      {!accountsQuery.isLoading &&
        !accountsQuery.isError &&
        (filteredAccounts.length === 0 ? (
          <div className={classes.emptyState}>
            <div className={classes.emptyIcon} aria-hidden="true">
              <BankIcon size={28} weight="light" />
            </div>
            {rawAccounts.length === 0 ? (
              <>
                <Title order={3} size="h4">
                  No financial accounts established
                </Title>
                <Text size="sm" c="dimmed" maw={420}>
                  Create your first account to record cash flows, track stock holdings, and enforce double-entry invariants.
                </Text>
                <Button color="brand" size="sm" mt="sm" leftSection={<PlusIcon size={16} weight="bold" />} onClick={openCreateModal}>
                  Create First Account
                </Button>
              </>
            ) : !showArchived && rawAccounts.length > 0 && rawAccounts.every((a) => a.archived) ? (
              <>
                <Title order={3} size="h4">
                  All accounts are archived
                </Title>
                <Text size="sm" c="dimmed" maw={420}>
                  You have {archivedAccountsCount} archived account{archivedAccountsCount === 1 ? '' : 's'}. Turn on &ldquo;Show
                  Archived&rdquo; to view them, or establish a new active account.
                </Text>
                <Group gap="sm" mt="sm">
                  <Button variant="default" size="sm" onClick={() => setShowArchived(true)}>
                    Show Archived Accounts
                  </Button>
                  <Button color="brand" size="sm" leftSection={<PlusIcon size={16} weight="bold" />} onClick={openCreateModal}>
                    Create Account
                  </Button>
                </Group>
              </>
            ) : (
              <>
                <Title order={3} size="h4">
                  No accounts match your criteria
                </Title>
                <Text size="sm" c="dimmed" maw={420}>
                  Try clearing your search query or adjusting your mode and kind filters.
                </Text>
                <Button
                  variant="default"
                  size="sm"
                  mt="sm"
                  onClick={() => {
                    setSearchQuery('');
                    setModeFilter('ALL');
                    setKindFilter('ALL');
                  }}>
                  Reset Filters
                </Button>
              </>
            )}
          </div>
        ) : (
          <div className={classes.accountsGrid}>
            {filteredAccounts.map((account) => (
              <AccountCard key={account.id} account={account} />
            ))}
          </div>
        ))}

      {/* Account Creation Modal */}
      <CreateAccountModal opened={createModalOpened} onClose={closeCreateModal} />

      {/* Internal Transfer Modal */}
      <TransferModal opened={transferModalOpened} onClose={closeTransferModal} onSuccess={() => accountsQuery.refetch()} />
    </section>
  );
}
