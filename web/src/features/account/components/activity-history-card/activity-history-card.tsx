import { Alert, Badge, Button, Group, Select, Skeleton, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  ArrowClockwiseIcon,
  ArrowCounterClockwiseIcon,
  ArrowDownLeftIcon,
  ArrowsLeftRightIcon,
  ArrowUpRightIcon,
  BankIcon,
  ClockCounterClockwiseIcon,
  ReceiptIcon,
  SlidersIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '@/api/client';
import type { ActivityType, FinancialAccount } from '../../types';
import {
  formatCurrency,
  formatDateTime,
  getActivityTypeBadgeColor,
  getActivityTypeLabel,
  isCashFundingCapable
} from '../../utils/account-formatters';
import { useAccountDetailOverlay } from '../account-detail-overlay/account-detail-overlay-provider';
import { ActivityDetailModal } from '../activity-detail-modal/activity-detail-modal';
import classes from './activity-history-card.module.css';

interface ActivityHistoryCardProps {
  account: FinancialAccount;
  onActivityUpdated?: () => void;
}

function getActivityIcon(type: ActivityType) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return <ArrowDownLeftIcon size={20} weight="bold" color="var(--mantine-color-teal-6)" />;
    case 'CASH_WITHDRAWAL':
      return <ArrowUpRightIcon size={20} weight="bold" color="var(--mantine-color-orange-6)" />;
    case 'OWNED_TRANSFER':
      return <ArrowsLeftRightIcon size={20} weight="bold" color="var(--mantine-color-blue-6)" />;
    case 'OPENING_BALANCE':
      return <BankIcon size={20} weight="duotone" color="var(--mantine-color-indigo-6)" />;
    case 'REVERSAL':
      return <ArrowCounterClockwiseIcon size={20} weight="bold" color="var(--mantine-color-violet-6)" />;
    case 'RECONCILIATION_ADJUSTMENT':
      return <SlidersIcon size={20} weight="bold" color="var(--mantine-color-cyan-6)" />;
    default:
      return <ReceiptIcon size={20} weight="duotone" />;
  }
}

export function ActivityHistoryCard({ account, onActivityUpdated }: ActivityHistoryCardProps) {
  const { open } = useAccountDetailOverlay();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [sortOption, setSortOption] = useState('recordedAt,desc');
  const [typeFilter, setTypeFilter] = useState('ALL');

  const [selectedActivityId, setSelectedActivityId] = useState<string | null>(null);
  const [detailOpened, { open: openDetail, close: closeDetail }] = useDisclosure(false);

  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';

  const activitiesQuery = $api.useQuery(
    'get',
    '/api/v1/activities',
    {
      params: {
        query: {
          accountId: account.id,
          pageable: {
            page,
            size: 10,
            sort: [sortOption]
          }
        }
      }
    },
    {
      enabled: !isHoldings
    }
  );

  const rawItems = activitiesQuery.data?.items ?? [];

  // Track which activities have been reversed by another activity in this ledger
  const reversedActivityIds = new Set(
    rawItems.filter((a) => a.activityType === 'REVERSAL' && Boolean(a.reversesActivityId)).map((a) => a.reversesActivityId as string)
  );

  // Client-side filtering by activity type on current slice
  const items = rawItems.filter((item) => {
    if (typeFilter !== 'ALL' && item.activityType !== typeFilter) {
      return false;
    }
    return true;
  });

  const hasNext = activitiesQuery.data?.hasNext ?? false;

  function handleOpenDetail(id: string) {
    setSelectedActivityId(id);
    openDetail();
  }

  if (isHoldings) {
    return null;
  }

  return (
    <>
      <section className={classes.card} aria-labelledby="activity-history-title">
        {/* 1. Header */}
        <div className={classes.cardHeader}>
          <div className={classes.titleArea}>
            <ClockCounterClockwiseIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
            <Text id="activity-history-title" className={classes.cardTitle}>
              Cash Activity History
            </Text>
            {activitiesQuery.data && (
              <Badge color="gray" variant="light" size="sm">
                {rawItems.length} {rawItems.length === 1 ? 'entry' : 'entries'}
              </Badge>
            )}
          </div>

          <Group gap="xs">
            <Button
              variant="default"
              size="sm"
              className={classes.actionBtn}
              leftSection={<ArrowClockwiseIcon size={14} />}
              loading={activitiesQuery.isFetching}
              onClick={() => activitiesQuery.refetch()}
              aria-label="Refresh activity history">
              Refresh
            </Button>
          </Group>
        </div>

        {/* 2. Filter & Sort Toolbar */}
        <div className={classes.controlsBar}>
          <div className={classes.filterGroup}>
            <Select
              value={sortOption}
              onChange={(val) => {
                if (val) {
                  setSortOption(val);
                  setPage(0);
                }
              }}
              data={[
                { value: 'recordedAt,desc', label: 'Recorded: Newest First' },
                { value: 'recordedAt,asc', label: 'Recorded: Oldest First' },
                { value: 'effectiveAt,desc', label: 'Effective: Newest First' },
                { value: 'effectiveAt,asc', label: 'Effective: Oldest First' }
              ]}
              className={classes.filterSelect}
              aria-label="Sort activities by date"
            />

            <Select
              value={typeFilter}
              onChange={(val) => {
                if (val) {
                  setTypeFilter(val);
                  setPage(0);
                }
              }}
              data={[
                { value: 'ALL', label: 'All Activities' },
                { value: 'CASH_DEPOSIT', label: 'Deposits' },
                { value: 'CASH_WITHDRAWAL', label: 'Withdrawals' },
                { value: 'OWNED_TRANSFER', label: 'Transfers' },
                { value: 'OPENING_BALANCE', label: 'Opening Balances' },
                { value: 'REVERSAL', label: 'Reversals' },
                { value: 'RECONCILIATION_ADJUSTMENT', label: 'Statement Adjustments' }
              ]}
              className={classes.filterSelect}
              aria-label="Filter activities by type"
            />
          </div>
        </div>

        {/* 3. Activities List Content */}
        {activitiesQuery.isLoading ? (
          <Stack gap="sm">
            <Skeleton height={52} radius="sm" />
            <Skeleton height={52} radius="sm" />
            <Skeleton height={52} radius="sm" />
          </Stack>
        ) : activitiesQuery.isError ? (
          <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
            Could not load cash activities for this account.
            <Button size="sm" variant="outline" color="red" mt="xs" className={classes.actionBtn} onClick={() => activitiesQuery.refetch()}>
              Retry
            </Button>
          </Alert>
        ) : items.length === 0 ? (
          <div className={classes.emptyState}>
            <ReceiptIcon size={32} weight="duotone" color="var(--mantine-color-dimmed)" />
            <Text size="sm" fw={600}>
              {typeFilter !== 'ALL' ? 'No activities matching this filter' : 'No Cash Activities Recorded'}
            </Text>
            <Text size="xs" c="dimmed" maw={360}>
              {typeFilter !== 'ALL'
                ? 'Try selecting "All Activities" to view your full transaction history.'
                : 'Deposit or withdraw funds to establish transaction history for this account.'}
            </Text>
            {typeFilter === 'ALL' && !account.archived && isCashFundingCapable(account.kind) && (
              <Group gap="xs" mt="xs">
                <Button
                  size="md"
                  color="teal"
                  variant="light"
                  className={classes.actionBtn}
                  onClick={() =>
                    navigate({
                      to: '/app/accounts/$accountId/deposit',
                      params: { accountId: account.id },
                      resetScroll: false
                    })
                  }>
                  Deposit Cash
                </Button>
                <Button
                  size="md"
                  color="orange"
                  variant="light"
                  className={classes.actionBtn}
                  onClick={() =>
                    navigate({
                      to: '/app/accounts/$accountId/withdraw',
                      params: { accountId: account.id },
                      resetScroll: false
                    })
                  }>
                  Withdraw Cash
                </Button>
                <Button size="md" color="blue" variant="light" className={classes.actionBtn} onClick={() => open({ type: 'transfer' })}>
                  Transfer Funds
                </Button>
              </Group>
            )}
          </div>
        ) : (
          <div className={classes.activityList}>
            {items.map((act) => {
              // Find posting leg for this account
              const myPosting = act.postings.find((p) => p.accountId === account.id) ?? act.postings[0];
              const postingAmount = myPosting ? myPosting.amount : '0.00';
              const numAmount = Number.parseFloat(postingAmount);
              const isPositive = !Number.isNaN(numAmount) && numAmount > 0;
              const isNegative = !Number.isNaN(numAmount) && numAmount < 0;

              // Check if effective time differs from recorded time (> 1 min)
              const isTimeDiscrepancy = Math.abs(new Date(act.effectiveAt).getTime() - new Date(act.recordedAt).getTime()) > 60000;

              const isReversal = act.activityType === 'REVERSAL';
              const isReversedByOther = reversedActivityIds.has(act.id);

              return (
                <button
                  type="button"
                  key={act.id}
                  className={`${classes.activityRow} ${isReversal ? classes.activityRowReversed : ''} ${isReversedByOther ? classes.activityRowIsReversed : ''}`}
                  onClick={() => handleOpenDetail(act.id)}
                  aria-label={`View details for ${getActivityTypeLabel(act.activityType)} of ${formatCurrency(postingAmount, account.currency)}`}>
                  <div className={classes.activityMain}>
                    <div className={classes.iconWrap} aria-hidden="true">
                      {getActivityIcon(act.activityType)}
                    </div>

                    <div className={classes.activityMeta}>
                      <div className={classes.activityTitleRow}>
                        <span className={classes.activityTitle}>{getActivityTypeLabel(act.activityType)}</span>
                        <Badge color={getActivityTypeBadgeColor(act.activityType)} variant="light" size="xs">
                          {act.activityType}
                        </Badge>
                        {isReversedByOther && (
                          <Badge color="violet" variant="outline" size="xs">
                            Reversed
                          </Badge>
                        )}
                        {isTimeDiscrepancy && (
                          <Badge color="blue" variant="outline" size="xs">
                            Historical Fact
                          </Badge>
                        )}
                        {act.policyDecision === 'CONFIRMED_BREACH' && (
                          <Badge color="red" variant="filled" size="xs">
                            Overdraft
                          </Badge>
                        )}
                      </div>

                      <div className={classes.activityTimes}>
                        <span>
                          Effective: <span className={classes.effectiveTime}>{formatDateTime(act.effectiveAt)}</span>
                        </span>
                        <span>&bull;</span>
                        <span className={classes.recordedTime}>Recorded: {formatDateTime(act.recordedAt)}</span>
                      </div>
                    </div>
                  </div>

                  <div className={classes.activityRight}>
                    <div className={classes.amountArea}>
                      <div
                        className={`${classes.amountText} ${
                          isPositive ? classes.amountPositive : isNegative ? classes.amountNegative : ''
                        }`}>
                        {isPositive ? '+' : ''}
                        {formatCurrency(postingAmount, account.currency)}
                      </div>
                      <Text size="xs" c="dimmed">
                        {act.postings.length} {act.postings.length === 1 ? 'posting' : 'postings'}
                      </Text>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {/* 4. Pageable Slice Controls */}
        {!isHoldings && (rawItems.length > 0 || page > 0) && (
          <div className={classes.paginationBar}>
            <span className={classes.pageIndicator}>Page {page + 1}</span>

            <div className={classes.paginationButtons}>
              <Button
                variant="default"
                size="sm"
                className={classes.pageBtn}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0 || activitiesQuery.isFetching}
                aria-label="Previous activities page">
                Previous
              </Button>

              <Button
                variant="default"
                size="sm"
                className={classes.pageBtn}
                onClick={() => setPage((p) => p + 1)}
                disabled={!hasNext || activitiesQuery.isFetching}
                aria-label="Next activities page">
                Next
              </Button>
            </div>
          </div>
        )}
      </section>

      {/* 5. Detail Modal */}
      <ActivityDetailModal
        activityId={selectedActivityId}
        opened={detailOpened}
        onClose={closeDetail}
        isAccountArchived={account.archived}
        isAlreadyReversed={Boolean(selectedActivityId && reversedActivityIds.has(selectedActivityId))}
        onActivityUpdated={() => {
          activitiesQuery.refetch();
          onActivityUpdated?.();
        }}
      />
    </>
  );
}
