import { Alert, Badge, Button, Group, Select, Skeleton, Stack, Text } from '@mantine/core';
import { ArrowClockwiseIcon, ClockCounterClockwiseIcon, ReceiptIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { toFinancialDecimal } from '@/shared/finance/decimal';
import { formatDateTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import { isCashFundingCapable } from '../../account-domain';
import { ActivityTypeIcon } from '../../activity-icon';
import { getActivityTypeLabel, getRecordingModeLabel } from '../../activity-presentation';
import type { FinancialAccount } from '../../types';
import classes from './activity-history-card.module.css';

export interface ActivityHistoryCardProps {
  account: FinancialAccount;
  onOpenDetail?: (activityId: string, isAlreadyReversed: boolean) => void;
  onDepositCash?: () => void;
  onWithdrawCash?: () => void;
  onRecordFee?: () => void;
  onAddInterest?: () => void;
  onTransferFunds?: () => void;
}

export function ActivityHistoryCard({
  account,
  onOpenDetail,
  onDepositCash,
  onWithdrawCash,
  onRecordFee,
  onAddInterest,
  onTransferFunds
}: ActivityHistoryCardProps) {
  const [page, setPage] = useState(0);
  const [sortOption, setSortOption] = useState('recordedAt,desc');
  const [typeFilter, setTypeFilter] = useState('ALL');

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
    onOpenDetail?.(id, reversedActivityIds.has(id));
  }

  if (isHoldings) {
    return null;
  }

  return (
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
              { value: 'CASH_FEE', label: 'Fees' },
              { value: 'CASH_INTEREST_CREDIT', label: 'Interest Credits' },
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
              <Button size="md" color="teal" variant="light" className={classes.actionBtn} onClick={onDepositCash}>
                Deposit Cash
              </Button>
              <Button size="md" color="orange" variant="light" className={classes.actionBtn} onClick={onWithdrawCash}>
                Withdraw Cash
              </Button>
              <Button size="md" color="red" variant="light" className={classes.actionBtn} onClick={onRecordFee}>
                Record Fee
              </Button>
              <Button size="md" color="cyan" variant="light" className={classes.actionBtn} onClick={onAddInterest}>
                Add Interest
              </Button>
              <Button size="md" color="blue" variant="light" className={classes.actionBtn} onClick={onTransferFunds}>
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
            const amountDec = toFinancialDecimal(postingAmount);
            const isPositive = amountDec?.isPositive() ?? false;
            const isNegative = amountDec?.isNegative() ?? false;

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
                aria-label={`View details for ${getActivityTypeLabel(act.activityType)} of ${formatMoney(postingAmount, account.currency)}`}>
                <div className={classes.activityMain}>
                  <div className={classes.iconWrap} aria-hidden="true">
                    <ActivityTypeIcon type={act.activityType} size={20} />
                  </div>

                  <div className={classes.activityMeta}>
                    <div className={classes.activityTitleRow}>
                      <span className={classes.activityTitle}>{getActivityTypeLabel(act.activityType)}</span>
                      {isReversedByOther && (
                        <Badge color="violet" variant="outline" size="xs">
                          Reversed
                        </Badge>
                      )}
                      {isTimeDiscrepancy && (
                        <Badge color="blue" variant="outline" size="xs">
                          {getRecordingModeLabel('HISTORICAL_FACT')}
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
                      className={`${classes.amountText} ${isPositive ? classes.amountPositive : isNegative ? classes.amountNegative : ''}`}>
                      {isPositive ? '+' : ''}
                      {formatMoney(postingAmount, account.currency)}
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
  );
}
