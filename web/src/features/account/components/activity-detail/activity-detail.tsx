import { Alert, Badge, Button, Group, Skeleton, Stack, Text } from '@mantine/core';
import {
  ArrowCounterClockwiseIcon,
  CalendarCheckIcon,
  ClockCounterClockwiseIcon,
  DatabaseIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import {
  formatCurrency,
  formatDateTime,
  getActivityTypeBadgeColor,
  getActivityTypeDescription,
  getActivityTypeLabel,
  getPolicyDecisionBadgeColor,
  getPolicyDecisionLabel,
  getPostingRoleLabel,
  getRecordingModeLabel
} from '../../utils/account-formatters';
import { ReverseActivityOverlay } from '../reverse-activity/reverse-activity';
import classes from './activity-detail.module.css';

interface ActivityDetailProps {
  activityId: string;
  isAccountArchived?: boolean;
  isAlreadyReversed?: boolean;
}

export function ActivityDetail({ activityId, isAccountArchived = false, isAlreadyReversed = false }: ActivityDetailProps) {
  const current = useCurrentOverlay();

  const activityQuery = $api.useQuery('get', '/api/v1/activities/{activityId}', {
    params: { path: { activityId: activityId! } }
  });

  const activity = activityQuery.data;

  function handleClose() {
    current.dismiss('cancelled');
  }

  // Determine if this activity can be reversed:
  // Must be deposit, withdrawal, or transfer; not already reversed; account not archived
  const isEligibleForReversal =
    activity &&
    !isAccountArchived &&
    !isAlreadyReversed &&
    (activity.activityType === 'CASH_DEPOSIT' ||
      activity.activityType === 'CASH_WITHDRAWAL' ||
      activity.activityType === 'OWNED_TRANSFER') &&
    !activity.reversesActivityId;

  const isReversalEntry = activity?.activityType === 'REVERSAL';

  return (
    <>
      {activityQuery.isLoading ? (
        <Stack gap="md">
          <Skeleton height={36} radius="sm" />
          <Skeleton height={100} radius="md" />
          <Skeleton height={140} radius="md" />
        </Stack>
      ) : activityQuery.isError || !activity ? (
        <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
          Could not load details for this transaction. It may have been modified or removed.
        </Alert>
      ) : (
        <div className={classes.container}>
          {/* 1. Header Badges & Description */}
          <Stack gap="xs">
            <Group gap="xs" wrap="wrap">
              <Badge color={getActivityTypeBadgeColor(activity.activityType)} variant="filled" size="md">
                {getActivityTypeLabel(activity.activityType)}
              </Badge>
              <Badge color="gray" variant="outline" size="sm">
                {getRecordingModeLabel(activity.recordingMode)}
              </Badge>
              {activity.policyDecision !== 'NOT_APPLICABLE' && (
                <Badge color={getPolicyDecisionBadgeColor(activity.policyDecision)} variant="light" size="sm">
                  {getPolicyDecisionLabel(activity.policyDecision)}
                </Badge>
              )}
              <Badge color="gray" variant="subtle" size="sm">
                Source: {activity.sourceKind}
              </Badge>
            </Group>

            <Text size="xs" c="dimmed">
              {getActivityTypeDescription(activity.activityType)}
            </Text>
          </Stack>

          {/* 2. Reversal Alerts */}
          {isReversalEntry && activity.reversesActivityId && (
            <Alert icon={<ArrowCounterClockwiseIcon size={18} weight="bold" />} color="violet" variant="light">
              This transaction is a ledger reversal that negated original transaction{' '}
              <Text component="span" fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
                {activity.reversesActivityId.slice(0, 8)}...
              </Text>
            </Alert>
          )}

          {isAlreadyReversed && (
            <Alert icon={<ArrowCounterClockwiseIcon size={18} weight="bold" />} color="violet" variant="light">
              This transaction has already been reversed by an offsetting correction entry in the ledger.
            </Alert>
          )}

          {/* 3. Effective Time vs Recorded Time Comparison */}
          <div className={classes.timeComparisonGrid}>
            <div className={classes.timeCard}>
              <div className={classes.timeHeader}>
                <CalendarCheckIcon size={16} weight="bold" color="var(--mantine-primary-color-filled)" />
                <span>Effective Date &amp; Time</span>
              </div>
              <div className={classes.timeValue}>{formatDateTime(activity.effectiveAt)}</div>
              <div className={classes.timeHelp}>The economic value date when funds cleared and changed your balance.</div>
            </div>

            <div className={classes.timeCard}>
              <div className={classes.timeHeader}>
                <DatabaseIcon size={16} weight="bold" color="var(--mantine-color-gray-6)" />
                <span>Recorded Date &amp; Time</span>
              </div>
              <div className={classes.timeValue}>{formatDateTime(activity.recordedAt)}</div>
              <div className={classes.timeHelp}>The system audit timestamp when this entry was written into the ledger.</div>
            </div>
          </div>

          {/* Time disparity indicator */}
          {activity.recordingMode === 'HISTORICAL_FACT' && (
            <Alert icon={<ClockCounterClockwiseIcon size={18} />} color="blue" variant="light">
              This transaction was recorded as a historical fact. The effective date is earlier than the system recording timestamp.
            </Alert>
          )}

          {/* 4. Ledger Postings Breakdown */}
          <div className={classes.postingsSection}>
            <span className={classes.sectionTitle}>Double-Entry Postings ({activity.postings.length})</span>

            <div className={classes.postingsList}>
              {activity.postings.map((posting) => {
                const num = Number.parseFloat(posting.amount);
                const isPositive = !Number.isNaN(num) && num > 0;
                const isNegative = !Number.isNaN(num) && num < 0;

                return (
                  <div key={`${posting.pocketId}-${posting.role}-${posting.amount}`} className={classes.postingRow}>
                    <div className={classes.postingInfo}>
                      <Group gap={6}>
                        <Badge size="xs" variant="light" color={isPositive ? 'teal' : isNegative ? 'orange' : 'gray'}>
                          {getPostingRoleLabel(posting.role)}
                        </Badge>
                        <span className={classes.postingRole}>{posting.currency} Cash Pocket</span>
                      </Group>
                      <span className={classes.postingMeta}>
                        Pocket: {posting.pocketId.slice(0, 8)}... &bull; Account: {posting.accountId.slice(0, 8)}...
                      </span>
                    </div>

                    <div
                      className={`${classes.postingAmount} ${
                        isPositive ? classes.postingAmountPositive : isNegative ? classes.postingAmountNegative : ''
                      }`}>
                      {isPositive ? '+' : ''}
                      {formatCurrency(posting.amount, posting.currency)}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* 5. Identifiers Strip */}
          <Text size="xs" c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
            Activity Reference ID: {activity.id}
          </Text>

          {/* 6. Action Controls */}
          <div className={classes.actions}>
            <Button variant="default" size="md" className={classes.actionBtn} onClick={handleClose}>
              Close
            </Button>

            {isEligibleForReversal && (
              <Button
                color="violet"
                variant="light"
                size="md"
                className={classes.actionBtn}
                leftSection={<ArrowCounterClockwiseIcon size={16} weight="bold" />}
                onClick={() => ReverseActivityOverlay.open({ activityId: activity.id })}>
                Reverse Transaction
              </Button>
            )}
          </div>
        </div>
      )}
    </>
  );
}

export const ActivityDetailOverlay = registerOverlay(ActivityDetail, {
  name: 'activity-detail',
  title: 'Transaction Details',
  presentation: 'modal',
  size: 'lg'
});
