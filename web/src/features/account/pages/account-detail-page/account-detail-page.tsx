import { Alert, Anchor, Badge, Button, Group, Skeleton, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { ArchiveIcon, ArrowClockwiseIcon, ArrowLeftIcon, GearIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { $api } from '@/api/client';
import { AccountSettingsModal } from '../../components/account-settings-modal/account-settings-modal';
import { ArchiveAccountModal } from '../../components/archive-account-modal/archive-account-modal';
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
  getTrackingModeLabel
} from '../../utils/account-formatters';
import classes from './account-detail-page.module.css';

interface AccountDetailPageProps {
  accountId: string;
}

export function AccountDetailPage({ accountId }: AccountDetailPageProps) {
  const [settingsOpened, { open: openSettings, close: closeSettings }] = useDisclosure(false);
  const [archiveOpened, { open: openArchive, close: closeArchive }] = useDisclosure(false);

  // Authoritative queries
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const isHoldings = accountQuery.data?.trackingMode === 'HOLDINGS_ONLY';

  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId } }
    },
    {
      enabled: !isHoldings && Boolean(accountQuery.data)
    }
  );

  async function handleRefetchAll() {
    await accountQuery.refetch();
    if (!isHoldings) {
      await balanceQuery.refetch();
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
          <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => accountQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      </div>
    );
  }

  const account = accountQuery.data;
  const balance = balanceQuery.data;

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
        <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Account In Policy Breach" color="red" variant="filled">
          This account has exceeded its configured negative balance or authorized overdraft parameters. Inflows or manual adjustments may be
          required to restore balanced standing.
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
          <Text className={classes.cardTitle}>Balance &amp; Liquidity Overview</Text>
          {balance && (
            <Badge color="teal" variant="light" size="xs">
              Projection: {balance.projectionStatus}
            </Badge>
          )}
        </div>

        {isHoldings ? (
          <Alert icon={<InfoIcon size={20} />} title="Holdings-Only Portfolio" color="indigo" variant="light">
            This brokerage account tracks equity lots and investment positions directly without maintaining double-entry cash ledger
            balances.
          </Alert>
        ) : balanceQuery.isLoading ? (
          <Skeleton height={120} radius="md" />
        ) : balance ? (
          <>
            <div className={classes.balanceHero}>
              <span className={classes.balanceLabel}>Cleared / Settled Balance</span>
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

      {/* 4. Detailed Account Specifications & Policies */}
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
    </section>
  );
}
