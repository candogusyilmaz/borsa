import { Alert, Anchor, Badge, Button, Group, Skeleton, Text, TextInput, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  ArchiveIcon,
  ArrowClockwiseIcon,
  ArrowDownLeftIcon,
  ArrowLeftIcon,
  ArrowsLeftRightIcon,
  ArrowUpRightIcon,
  CalendarBlankIcon,
  ClockCounterClockwiseIcon,
  GearIcon,
  InfoIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { AccountSettingsModal } from '../../components/account-settings-modal/account-settings-modal';
import { ActivityHistoryCard } from '../../components/activity-history-card/activity-history-card';
import { ArchiveAccountModal } from '../../components/archive-account-modal/archive-account-modal';
import { CashPocketCard } from '../../components/cash-pocket-card/cash-pocket-card';
import { OpeningCorrectionModal } from '../../components/opening-correction-modal/opening-correction-modal';
import { OpeningStateCard } from '../../components/opening-state-card/opening-state-card';
import { RecordCashActivityModal } from '../../components/record-cash-activity-modal/record-cash-activity-modal';
import { TransferModal } from '../../components/transfer-modal/transfer-modal';
import {
  formatCurrency,
  formatDateTime,
  getAccountKindBadgeColor,
  getAccountKindDescription,
  getAccountKindLabel,
  getPolicyDescription,
  getPolicyLabel,
  getTrackingModeBadgeColor,
  getTrackingModeDescription,
  getTrackingModeLabel,
  isCashFundingCapable,
  toDatetimeLocal
} from '../../utils/account-formatters';
import classes from './account-detail-page.module.css';

interface AccountDetailPageProps {
  accountId: string;
}

export function AccountDetailPage({ accountId }: AccountDetailPageProps) {
  const [settingsOpened, { open: openSettings, close: closeSettings }] = useDisclosure(false);
  const [archiveOpened, { open: openArchive, close: closeArchive }] = useDisclosure(false);
  const [openingCorrectionOpened, { open: openOpeningCorrection, close: closeOpeningCorrection }] = useDisclosure(false);
  const [depositOpened, { open: openDeposit, close: closeDeposit }] = useDisclosure(false);
  const [withdrawOpened, { open: openWithdraw, close: closeWithdraw }] = useDisclosure(false);
  const [transferOpened, { open: openTransfer, close: closeTransfer }] = useDisclosure(false);

  // Historical effective date state for balance queries
  const [selectedAsOf, setSelectedAsOf] = useState<string | null>(null);
  const [asOfInput, setAsOfInput] = useState<string>(toDatetimeLocal(new Date(Date.now() - 60000)));

  // Authoritative queries
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const isHoldings = accountQuery.data?.trackingMode === 'HOLDINGS_ONLY';

  const isFutureAsOf = Boolean(selectedAsOf && new Date(selectedAsOf).getTime() > Date.now());

  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId } },
      query: selectedAsOf && !isFutureAsOf ? { asOf: selectedAsOf } : undefined
    },
    {
      enabled: !isHoldings && Boolean(accountQuery.data) && !isFutureAsOf
    }
  );

  const openingBalanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId } },
      query: accountQuery.data?.coverageFrom ? { asOf: accountQuery.data.coverageFrom } : undefined
    },
    {
      enabled: !isHoldings && Boolean(accountQuery.data?.coverageFrom)
    }
  );

  async function handleRefetchAll() {
    await accountQuery.refetch();
    if (!isHoldings) {
      await balanceQuery.refetch();
      await openingBalanceQuery.refetch();
    }
  }

  if (accountQuery.isLoading) {
    return (
      <div className={classes.container}>
        <Skeleton height={40} width={180} radius="md" />
        <Skeleton height={120} radius="md" />
        <Skeleton height={240} radius="md" />
      </div>
    );
  }

  if (accountQuery.isError || !accountQuery.data) {
    return (
      <div className={classes.container}>
        <Anchor component={Link} to="/app/accounts" className={classes.backLink} size="sm" c="dimmed">
          <Group gap={6}>
            <ArrowLeftIcon size={16} />
            <span>Back to Accounts</span>
          </Group>
        </Anchor>

        <Alert icon={<WarningCircleIcon size={20} />} title="Account Not Found" color="red" variant="light">
          Could not load the requested financial account. It may not exist or you may not have access.
          <Button size="sm" variant="outline" color="red" mt="xs" className={classes.actionBtn} onClick={() => accountQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      </div>
    );
  }

  const account = accountQuery.data;
  const balance = balanceQuery.data;
  const canTransactCash = !isHoldings && isCashFundingCapable(account.kind) && !account.archived;

  const isBeforeCoverage = Boolean(
    selectedAsOf && account.coverageFrom && new Date(selectedAsOf).getTime() < new Date(account.coverageFrom).getTime()
  );

  return (
    <section className={classes.container} aria-labelledby="account-title">
      {/* 1. Breadcrumb Link */}
      <div>
        <Anchor component={Link} to="/app/accounts" className={classes.backLink} size="sm" c="dimmed">
          <Group gap={6}>
            <ArrowLeftIcon size={16} />
            <span>Back to Financial Accounts</span>
          </Group>
        </Anchor>
      </div>

      {/* Policy breach alert banner if active */}
      {account.policyBreach && (
        <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Overdraft Limit Exceeded" color="red" variant="filled">
          This account has exceeded its overdraft limit. Please add funds to restore a positive balance.
        </Alert>
      )}

      {/* Archived banner if archived */}
      {account.archived && (
        <Alert icon={<ArchiveIcon size={20} weight="bold" />} title="Archived Financial Account" color="gray" variant="light">
          This account was archived on {formatDateTime(account.archivedAt)}. Active operations and outgoing postings are restricted.
        </Alert>
      )}

      {/* 2. Header & Action Controls */}
      <div className={classes.headerRow}>
        <div className={classes.headerInfo}>
          <div className={classes.titleArea}>
            <Title order={1} id="account-title" className={classes.title}>
              {account.name}
            </Title>
            <Badge variant="outline" color="gray" size="md">
              v{account.version ?? 0}
            </Badge>
          </div>

          <div className={classes.badgesGroup}>
            <Badge color={getAccountKindBadgeColor(account.kind)} variant="light" size="sm">
              {getAccountKindLabel(account.kind)}
            </Badge>
            <Badge color={getTrackingModeBadgeColor(account.trackingMode)} variant="light" size="sm">
              {getTrackingModeLabel(account.trackingMode)}
            </Badge>
            <Badge color="teal" variant="outline" size="sm">
              {account.currency}
            </Badge>
            {account.archived && (
              <Badge color="gray" variant="filled" size="sm">
                Archived
              </Badge>
            )}
          </div>
        </div>

        <div className={classes.actionsBar}>
          {canTransactCash && (
            <>
              <Button
                color="teal"
                variant="filled"
                size="md"
                leftSection={<ArrowDownLeftIcon size={18} weight="bold" />}
                onClick={openDeposit}
                aria-label="Deposit cash">
                Deposit
              </Button>

              <Button
                color="orange"
                variant="light"
                size="md"
                leftSection={<ArrowUpRightIcon size={18} weight="bold" />}
                onClick={openWithdraw}
                aria-label="Withdraw cash">
                Withdraw
              </Button>

              <Button
                color="blue"
                variant="light"
                size="md"
                leftSection={<ArrowsLeftRightIcon size={18} weight="bold" />}
                onClick={openTransfer}
                aria-label="Transfer cash">
                Transfer
              </Button>
            </>
          )}

          <Button
            variant="default"
            size="md"
            leftSection={<ArrowClockwiseIcon size={16} />}
            loading={accountQuery.isFetching || balanceQuery.isFetching}
            onClick={handleRefetchAll}
            aria-label="Refresh account details">
            Refresh
          </Button>

          <Button
            variant="default"
            size="md"
            leftSection={<GearIcon size={18} weight="bold" />}
            onClick={openSettings}
            disabled={account.archived}
            aria-label="Edit account settings">
            Settings &amp; Policies
          </Button>

          <Button
            color="red"
            variant="light"
            size="md"
            leftSection={<ArchiveIcon size={18} weight="bold" />}
            onClick={openArchive}
            disabled={account.archived}
            aria-label="Archive account">
            Archive
          </Button>
        </div>
      </div>

      {/* 3. Balances & Ledgers Card */}
      <div className={classes.card}>
        <div className={classes.cardHeader}>
          <div className={classes.titleArea}>
            <Text className={classes.cardTitle}>Balance &amp; Liquidity Overview</Text>
            {balance && (
              <Badge color={selectedAsOf ? 'indigo' : 'teal'} variant="light" size="xs">
                {selectedAsOf
                  ? 'Historical Snapshot'
                  : balance.projectionStatus === 'CURRENT'
                    ? 'Live: Up to date'
                    : balance.projectionStatus === 'REBUILDING'
                      ? 'Updating...'
                      : balance.projectionStatus === 'STALE'
                        ? 'Out of date'
                        : `Status: ${balance.projectionStatus}`}
              </Badge>
            )}
          </div>

          {!isHoldings && (
            <Button
              variant={selectedAsOf ? 'default' : 'subtle'}
              color="brand"
              size="sm"
              className={classes.actionBtn}
              onClick={() => {
                if (selectedAsOf) {
                  setSelectedAsOf(null);
                  setAsOfInput('');
                }
              }}
              disabled={!selectedAsOf}
              leftSection={<ClockCounterClockwiseIcon size={14} weight="bold" />}>
              Live Balance
            </Button>
          )}
        </div>

        {/* Historical Effective Date Selector Bar */}
        {!isHoldings && (
          <div className={classes.asOfToolbar}>
            <div className={classes.asOfControls}>
              <div className={classes.asOfLabelGroup}>
                <CalendarBlankIcon size={18} weight="bold" color="var(--mantine-primary-color-filled)" />
                <Text size="xs" fw={600}>
                  Historical As-Of Date:
                </Text>
              </div>
              <TextInput
                type="datetime-local"
                size="sm"
                value={asOfInput}
                onChange={(e) => {
                  const val = e.currentTarget.value;
                  setAsOfInput(val);
                  if (!val) {
                    setSelectedAsOf(null);
                    return;
                  }
                  const parsed = new Date(val);
                  if (!Number.isNaN(parsed.getTime())) {
                    setSelectedAsOf(parsed.toISOString());
                  }
                }}
                className={classes.asOfInput}
                aria-label="Select historical date for balance snapshot"
              />
            </div>

            <div className={classes.asOfPresetContainer}>
              <Text size="xs" c="dimmed" className={classes.asOfPresetHeading}>
                Presets:
              </Text>
              <div className={classes.asOfPresetGrid}>
                {account.coverageFrom && (
                  <Button
                    variant="light"
                    color="teal"
                    className={classes.asOfPresetBtn}
                    onClick={() => {
                      setSelectedAsOf(account.coverageFrom ?? null);
                      if (account.coverageFrom) {
                        setAsOfInput(toDatetimeLocal(new Date(account.coverageFrom)));
                      }
                    }}>
                    Opening Date
                  </Button>
                )}
                <Button
                  variant="default"
                  className={classes.asOfPresetBtn}
                  onClick={() => {
                    const d = new Date();
                    d.setHours(0, 0, 0, 0);
                    setSelectedAsOf(d.toISOString());
                    setAsOfInput(toDatetimeLocal(d));
                  }}>
                  Today (00:00)
                </Button>
                <Button
                  variant="default"
                  className={classes.asOfPresetBtn}
                  onClick={() => {
                    const d = new Date();
                    d.setDate(d.getDate() - 1);
                    d.setHours(23, 59, 59, 999);
                    setSelectedAsOf(d.toISOString());
                    setAsOfInput(toDatetimeLocal(d));
                  }}>
                  Yesterday
                </Button>
                <Button
                  variant="default"
                  className={classes.asOfPresetBtn}
                  onClick={() => {
                    const d = new Date();
                    d.setDate(1);
                    d.setHours(0, 0, 0, 0);
                    setSelectedAsOf(d.toISOString());
                    setAsOfInput(toDatetimeLocal(d));
                  }}>
                  Start of Month
                </Button>
                {selectedAsOf && (
                  <Button
                    variant="default"
                    className={classes.asOfResetBtn}
                    onClick={() => {
                      setSelectedAsOf(null);
                      setAsOfInput('');
                    }}>
                    Reset to Live
                  </Button>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Historical Context Alerts */}
        {selectedAsOf && isFutureAsOf && (
          <Alert icon={<WarningCircleIcon size={20} />} title="Future Date Not Allowed" color="red" variant="light">
            <Text size="sm">
              Balance snapshots cannot be calculated for future dates ({formatDateTime(selectedAsOf)}). Please select a past or current
              timestamp.
            </Text>
            <Button
              size="sm"
              variant="outline"
              color="red"
              mt="xs"
              className={classes.actionBtn}
              onClick={() => {
                setSelectedAsOf(null);
                setAsOfInput('');
              }}>
              Reset to Live Balance
            </Button>
          </Alert>
        )}

        {selectedAsOf && !isFutureAsOf && isBeforeCoverage && (
          <Alert icon={<InfoIcon size={20} />} title="Historical Date Precedes Account Opening" color="orange" variant="light">
            The selected date ({formatDateTime(selectedAsOf)}) precedes this account&apos;s opening date (
            {formatDateTime(account.coverageFrom)}). No ledger cash movements exist prior to account establishment.
            {account.coverageFrom && (
              <Button
                size="sm"
                variant="outline"
                color="orange"
                mt="xs"
                className={classes.actionBtn}
                onClick={() => {
                  setSelectedAsOf(account.coverageFrom ?? null);
                  setAsOfInput(toDatetimeLocal(new Date(account.coverageFrom!)));
                }}>
                Jump to Opening Date ({formatDateTime(account.coverageFrom)})
              </Button>
            )}
          </Alert>
        )}

        {selectedAsOf && !isFutureAsOf && !isBeforeCoverage && balance && (
          <Alert icon={<ClockCounterClockwiseIcon size={20} />} title="Historical Balance Active" color="indigo" variant="light">
            Viewing balance as of {formatDateTime(balance.requestedAsOf || selectedAsOf)}. Postings after this moment are excluded from the
            totals below.
          </Alert>
        )}

        {isHoldings ? (
          <Alert icon={<InfoIcon size={20} />} title="Holdings-Only Portfolio" color="indigo" variant="light">
            This account tracks stock quantities directly without cash ledger bookkeeping.
          </Alert>
        ) : isFutureAsOf ? null : balanceQuery.isLoading ? (
          <Skeleton height={120} radius="md" />
        ) : balanceQuery.isError ? (
          <Alert icon={<WarningCircleIcon size={20} />} title="Balance Query Failed" color="red" variant="light">
            <Text size="sm">{normalizeError(balanceQuery.error).message || 'Could not load balance for the requested timestamp.'}</Text>
            <Group mt="xs" gap="xs">
              <Button size="sm" variant="outline" color="red" className={classes.actionBtn} onClick={() => balanceQuery.refetch()}>
                Retry
              </Button>
              <Button
                size="sm"
                variant="default"
                className={classes.actionBtn}
                onClick={() => {
                  setSelectedAsOf(null);
                  setAsOfInput('');
                }}>
                Reset to Live
              </Button>
            </Group>
          </Alert>
        ) : isBeforeCoverage ? null : balance ? (
          <>
            <div className={classes.balanceHero}>
              <span className={classes.balanceLabel}>
                {selectedAsOf ? 'Historical Cleared / Settled Balance' : 'Cleared / Settled Balance'}
              </span>
              <div className={classes.balanceValue}>
                {formatCurrency(balance.clearedBalance ?? balance.ledgerBalance, account.currency)}
              </div>
            </div>

            <div className={classes.metricsGrid}>
              <div className={classes.metricItem}>
                <span className={classes.metricLabel}>Ledger Balance</span>
                <span className={classes.metricValue}>{formatCurrency(balance.ledgerBalance, account.currency)}</span>
              </div>

              <div className={classes.metricItem}>
                <span className={classes.metricLabel}>Cash Held / Reserved</span>
                <span className={classes.metricValue}>{formatCurrency(balance.cashHeld, account.currency)}</span>
              </div>

              <div className={classes.metricItem}>
                <span className={classes.metricLabel}>Overdraft Used</span>
                <span className={classes.metricValue}>{formatCurrency(balance.overdraftUsed, account.currency)}</span>
              </div>

              <div className={classes.metricItem}>
                <span className={classes.metricLabel}>Credit Available</span>
                <span className={classes.metricValue}>{formatCurrency(balance.creditAvailable, account.currency)}</span>
              </div>
            </div>

            {balance.liabilityOutstanding && (
              <div className={classes.liabilityMetricItem}>
                <span className={classes.metricLabel}>Liability Outstanding</span>
                <span className={classes.liabilityMetricValue}>{formatCurrency(balance.liabilityOutstanding, account.currency)}</span>
              </div>
            )}
          </>
        ) : (
          <Alert icon={<InfoIcon size={20} />} color="blue" variant="light">
            No live balance snapshot available.
          </Alert>
        )}
      </div>

      {/* 4. Cash Settlement Pocket Card */}
      <CashPocketCard account={account} balance={balance} onOpenDeposit={openDeposit} onOpenWithdraw={openWithdraw} />

      {/* 5. Cash Activity History */}
      <ActivityHistoryCard
        account={account}
        onOpenDeposit={openDeposit}
        onOpenWithdraw={openWithdraw}
        onOpenTransfer={openTransfer}
        onActivityUpdated={handleRefetchAll}
      />

      {/* 6. Opening State & Cash Coverage Card */}
      <OpeningStateCard account={account} onOpenCorrection={openOpeningCorrection} />

      {/* 5. Detailed Account Specifications & Policies */}
      <div className={classes.card}>
        <div className={classes.cardHeader}>
          <Text className={classes.cardTitle}>Account Specifications &amp; Invariants</Text>
        </div>

        <div className={classes.specGrid}>
          <div className={classes.specRow}>
            <span className={classes.specLabel}>Account Kind</span>
            <span className={classes.specValue}>
              {getAccountKindLabel(account.kind)} — {getAccountKindDescription(account.kind)}
            </span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Tracking Mode</span>
            <span className={classes.specValue}>
              {getTrackingModeLabel(account.trackingMode)} — {getTrackingModeDescription(account.trackingMode)}
            </span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Negative Balance Policy</span>
            <span className={classes.specValue}>
              {getPolicyLabel(account.policy)}
              {account.policy && ` — ${getPolicyDescription(account.policy)}`}
            </span>
          </div>

          {account.authorizedLimit && (
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Authorized Overdraft Limit</span>
              <span className={classes.specValue}>{formatCurrency(account.authorizedLimit, account.currency)}</span>
            </div>
          )}

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Operating Currency</span>
            <span className={classes.specValue}>{account.currency}</span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Assigned Time Zone</span>
            <span className={classes.specValue}>{account.timeZone}</span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Cash Coverage Status</span>
            <span className={classes.specValue}>
              {account.cashCoverageStatus}
              {account.coverageFrom && ` (Effective from ${formatDateTime(account.coverageFrom)})`}
            </span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Source Kind &amp; System Version</span>
            <span className={classes.specValue}>
              {account.sourceKind} &bull; Concurrency Revision: v{account.version ?? 0}
            </span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Created At</span>
            <span className={classes.specValue}>{formatDateTime(account.createdAt)}</span>
          </div>

          <div className={classes.specRow}>
            <span className={classes.specLabel}>Last Updated</span>
            <span className={classes.specValue}>{formatDateTime(account.updatedAt)}</span>
          </div>
        </div>
      </div>

      {/* Settings Modal with Optimistic Version Conflict UX */}
      <AccountSettingsModal
        key={`settings-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={settingsOpened}
        onClose={closeSettings}
        onRefetchAccount={handleRefetchAll}
      />

      {/* Archive Confirmation Modal */}
      <ArchiveAccountModal
        key={`archive-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={archiveOpened}
        onClose={closeArchive}
        onRefetchAccount={handleRefetchAll}
      />

      {/* Opening Correction Modal */}
      <OpeningCorrectionModal
        key={`opening-correction-${account.id}-${account.version ?? 0}-${openingBalanceQuery.data?.ledgerBalance ?? 'pending'}`}
        account={account}
        currentOpeningBalance={openingBalanceQuery.data?.ledgerBalance}
        opened={openingCorrectionOpened}
        onClose={closeOpeningCorrection}
        onRefetchAccount={handleRefetchAll}
      />

      {/* Record Deposit Modal */}
      <RecordCashActivityModal
        key={`deposit-${account.id}-${balance?.ledgerBalance ?? 0}`}
        account={account}
        balance={balance}
        opened={depositOpened}
        onClose={closeDeposit}
        defaultType="CASH_DEPOSIT"
        onSuccess={handleRefetchAll}
      />

      {/* Record Withdrawal Modal */}
      <RecordCashActivityModal
        key={`withdraw-${account.id}-${balance?.ledgerBalance ?? 0}`}
        account={account}
        balance={balance}
        opened={withdrawOpened}
        onClose={closeWithdraw}
        defaultType="CASH_WITHDRAWAL"
        onSuccess={handleRefetchAll}
      />

      {/* Internal Transfer Modal */}
      <TransferModal
        key={`transfer-${account.id}`}
        opened={transferOpened}
        onClose={closeTransfer}
        defaultSourceAccountId={account.id}
        lockSourceAccount={true}
        onSuccess={handleRefetchAll}
      />
    </section>
  );
}
