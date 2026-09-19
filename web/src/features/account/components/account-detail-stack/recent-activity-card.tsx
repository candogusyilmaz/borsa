import { Badge, Collapse, Skeleton } from '@mantine/core';
import {
  ArrowCounterClockwiseIcon,
  ArrowDownLeftIcon,
  ArrowsLeftRightIcon,
  ArrowUpRightIcon,
  BankIcon,
  CaretDownIcon,
  CaretUpIcon,
  ClockCounterClockwiseIcon,
  ReceiptIcon,
  SlidersIcon,
  TrendUpIcon
} from '@phosphor-icons/react';
import { $api } from '@/api/client';
import type { ActivityResponse, ActivityType, FinancialAccount } from '../../types';
import { formatCurrency, formatDateTime, getActivityTypeLabel } from '../../utils/account-formatters';
import { ActivityDetailOverlay } from '../activity-detail/activity-detail';
import classes from './recent-activity-card.module.css';

interface RecentActivityCardProps {
  account: FinancialAccount;
  expanded: boolean;
  onToggle: () => void;
  onViewAll: () => void;
}

function getActivityIcon(type: ActivityType) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return <ArrowDownLeftIcon size={18} weight="bold" color="var(--mantine-color-teal-6)" />;
    case 'CASH_WITHDRAWAL':
      return <ArrowUpRightIcon size={18} weight="bold" color="var(--mantine-color-orange-6)" />;
    case 'CASH_FEE':
      return <ReceiptIcon size={18} weight="bold" color="var(--mantine-color-red-6)" />;
    case 'CASH_INTEREST_CREDIT':
      return <TrendUpIcon size={18} weight="bold" color="var(--mantine-color-cyan-6)" />;
    case 'OWNED_TRANSFER':
      return <ArrowsLeftRightIcon size={18} weight="bold" color="var(--mantine-color-blue-6)" />;
    case 'OPENING_BALANCE':
      return <BankIcon size={18} weight="duotone" color="var(--mantine-color-indigo-6)" />;
    case 'REVERSAL':
      return <ArrowCounterClockwiseIcon size={18} weight="bold" color="var(--mantine-color-violet-6)" />;
    case 'RECONCILIATION_ADJUSTMENT':
      return <SlidersIcon size={18} weight="bold" color="var(--mantine-color-cyan-6)" />;
    default:
      return <ReceiptIcon size={18} weight="duotone" />;
  }
}

export function RecentActivityCard({ account, expanded, onToggle, onViewAll }: RecentActivityCardProps) {
  const activitiesQuery = $api.useQuery('get', '/api/v1/activities', {
    params: {
      query: {
        accountId: account.id,
        pageable: { page: 0, size: 5, sort: ['recordedAt,desc'] }
      }
    }
  });

  const items = activitiesQuery.data?.items ?? [];
  const latestItem = items[0];

  function renderActivityRow(act: ActivityResponse, isSingle = false) {
    const myPosting = act.postings.find((p) => p.accountId === account.id) ?? act.postings[0];
    const postingAmount = myPosting ? myPosting.amount : '0.00';
    const numAmount = Number.parseFloat(postingAmount);
    const isPositive = !Number.isNaN(numAmount) && numAmount > 0;
    const isNegative = !Number.isNaN(numAmount) && numAmount < 0;

    return (
      <button
        type="button"
        key={act.id}
        className={`${classes.activityRow} ${isSingle ? classes.activityRowSingle : ''}`}
        onClick={(e) => {
          e.stopPropagation();
          ActivityDetailOverlay.open({ activityId: act.id, isAccountArchived: account.archived });
        }}
        aria-label={`View details for ${getActivityTypeLabel(act.activityType)}: ${formatCurrency(postingAmount, account.currency)}`}>
        <div className={classes.activityItemLeft}>
          <div className={classes.activityIconWrap} aria-hidden="true">
            {getActivityIcon(act.activityType)}
          </div>
          <div className={classes.activityMeta}>
            <div className={classes.activityTitleRow}>
              <span className={classes.activityTitle}>{getActivityTypeLabel(act.activityType)}</span>
              {act.activityType === 'REVERSAL' && (
                <Badge color="violet" variant="light" size="xs">
                  Reversed
                </Badge>
              )}
            </div>
            <span className={classes.activityDate}>{formatDateTime(act.effectiveAt)}</span>
          </div>
        </div>

        <div
          className={`${classes.activityAmount} ${
            isPositive ? classes.amountPositive : isNegative ? classes.amountNegative : classes.amountNeutral
          }`}>
          {isPositive ? '+' : ''}
          {formatCurrency(postingAmount, account.currency)}
        </div>
      </button>
    );
  }

  return (
    <section className={classes.card} aria-labelledby="recent-activity-title">
      {/* Accordion Header (No nested buttons) */}
      <div className={classes.cardHeader}>
        <button
          type="button"
          className={classes.headerMain}
          onClick={onToggle}
          aria-expanded={expanded}
          aria-controls="recent-activity-content">
          <div className={classes.headerLeft}>
            <div className={classes.iconSquircle} aria-hidden="true">
              <ClockCounterClockwiseIcon size={22} weight="duotone" />
            </div>
            <span id="recent-activity-title" className={classes.cardTitle}>
              Recent Activity
            </span>
          </div>
        </button>

        <div className={classes.headerRight}>
          {expanded && items.length > 0 && (
            <button type="button" className={classes.viewAllBtn} onClick={onViewAll} aria-label="View all account activities">
              View All
            </button>
          )}

          <button
            type="button"
            className={classes.chevronBtn}
            onClick={onToggle}
            aria-label={expanded ? 'Collapse Recent Activity' : 'Expand Recent Activity'}>
            <div className={classes.chevronIcon} aria-hidden="true">
              {expanded ? <CaretUpIcon size={18} weight="bold" /> : <CaretDownIcon size={18} weight="bold" />}
            </div>
          </button>
        </div>
      </div>

      {/* Card Body */}
      <div className={classes.cardBody} id="recent-activity-content">
        {activitiesQuery.isLoading ? (
          <div style={{ paddingBlock: '0.5rem' }}>
            <Skeleton height={44} radius="sm" />
          </div>
        ) : items.length === 0 ? (
          <div className={classes.emptyState}>No recent activity recorded.</div>
        ) : (
          <>
            {/* Always render latest activity without unmounting */}
            {latestItem && renderActivityRow(latestItem, !expanded || items.length === 1)}

            {/* Additional items expand smoothly below without duplicate mounting */}
            {items.length > 1 && (
              <Collapse expanded={expanded}>
                <div className={classes.expandedContent}>
                  {items.slice(1, 5).map((act, idx, arr) => renderActivityRow(act, idx === arr.length - 1))}
                </div>
              </Collapse>
            )}
          </>
        )}
      </div>
    </section>
  );
}
