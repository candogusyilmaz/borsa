import { Alert, Badge, Button, Divider, Group, Loader, Stack, Text } from '@mantine/core';
import { ArrowCounterClockwiseIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { formatDateTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import { useCurrentOverlay } from '@/shared/overlay';
import { ActivityDetailOverlay } from '../activity-detail/activity-detail';
import classes from './reconciliation.module.css';
import {
  getLifecycleStatusBadgeColor,
  getLifecycleStatusDescription,
  getLifecycleStatusLabel,
  getResolutionBadgeColor,
  getResolutionLabel
} from './reconciliation-domain';

interface ReconciliationDetailProps {
  reconciliationId: string;
  isAccountArchived?: boolean;
  onBack: () => void;
  onStartCorrection: () => void;
}

export function ReconciliationDetail({
  reconciliationId,
  isAccountArchived = false,
  onBack,
  onStartCorrection
}: ReconciliationDetailProps) {
  const current = useCurrentOverlay();
  const detailQuery = $api.useQuery('get', '/api/v1/reconciliations/{reconciliationId}', {
    params: {
      path: { reconciliationId }
    }
  });

  const rec = detailQuery.data;

  if (detailQuery.isLoading) {
    return (
      <Group justify="center" p="xl">
        <Loader size="sm" />
      </Group>
    );
  }

  if (detailQuery.isError || !rec) {
    return (
      <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
        Could not load reconciliation record.
        <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => detailQuery.refetch()}>
          Retry
        </Button>
      </Alert>
    );
  }

  const isSuperseded = rec.lifecycleStatus === 'SUPERSEDED';
  const isStale = rec.lifecycleStatus === 'STALE';
  const canCorrect = !isSuperseded && !isAccountArchived;

  return (
    <div className={classes.container}>
      <Stack gap="md">
        {/* 1. Header with Badges */}
        <div className={classes.headerBar}>
          <div>
            <Text fw={700} size="md">
              {rec.statementReference}
            </Text>
            <Text size="xs" c="dimmed">
              Reconciled on {formatDateTime(rec.createdAt)}
            </Text>
          </div>

          <Group gap={6}>
            <Badge color={getResolutionBadgeColor(rec.resolution)} variant="filled" size="sm">
              {getResolutionLabel(rec.resolution)}
            </Badge>
            <Badge color={getLifecycleStatusBadgeColor(rec.lifecycleStatus)} variant="light" size="sm">
              {getLifecycleStatusLabel(rec.lifecycleStatus)}
            </Badge>
          </Group>
        </div>

        {/* 2. Lifecycle Notice */}
        {isSuperseded && (
          <Alert icon={<ArrowCounterClockwiseIcon size={18} weight="bold" />} color="gray" variant="light">
            <Text size="xs">This reconciliation was superseded by a later correction entry and is retained for audit history.</Text>
          </Alert>
        )}

        {isStale && (
          <Alert icon={<InfoIcon size={18} />} color="orange" variant="light">
            <Text size="xs">{getLifecycleStatusDescription('STALE')}</Text>
          </Alert>
        )}

        {/* 3. Detail Specifications Card */}
        <div className={classes.comparisonCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Statement Period
          </Text>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Opening Date &amp; Time</span>
            <span className={classes.comparisonValue}>{formatDateTime(rec.statementOpeningAt)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Closing Date &amp; Time</span>
            <span className={classes.comparisonValue}>{formatDateTime(rec.statementClosingAt)}</span>
          </div>

          <Divider my={4} />

          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Balance Comparison
          </Text>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Statement Closing Balance</span>
            <span className={classes.comparisonValue}>{formatMoney(rec.statementClosingBalance, rec.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Ledger Closing Balance (Prior to Adj.)</span>
            <span className={classes.comparisonValue}>{formatMoney(rec.ledgerClosingBalanceBeforeAdjustment, rec.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Statement Opening Balance</span>
            <span className={classes.comparisonValue}>{formatMoney(rec.statementOpeningBalance, rec.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Ledger Opening Balance</span>
            <span className={classes.comparisonValue}>{formatMoney(rec.ledgerOpeningBalance, rec.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Net Period Movement</span>
            <span className={classes.comparisonValue}>{formatMoney(rec.periodNetPostedAmount, rec.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Period Postings</span>
            <span className={classes.comparisonValue}>
              {rec.periodPostingCount ?? 0} ({rec.totalPostingCountThroughClosing ?? 0} total through period)
            </span>
          </div>
        </div>

        {/* 4. Adjustment Activity Information */}
        {rec.resolution === 'ADJUSTED' && (
          <div className={classes.comparisonCard}>
            <Text size="xs" fw={700} c="dimmed" tt="uppercase">
              Adjustment Details
            </Text>

            <div className={classes.comparisonRow}>
              <span className={classes.comparisonLabel}>Adjustment Posted</span>
              <span className={classes.comparisonValue} style={{ color: 'var(--mantine-color-orange-6)' }}>
                {formatMoney(rec.adjustmentAmount || rec.closingDifference, rec.currency, { adaptivePrecision: true })}
              </span>
            </div>

            {rec.adjustmentReason && (
              <div className={classes.comparisonRow}>
                <span className={classes.comparisonLabel}>Reason</span>
                <span className={classes.comparisonValue}>{rec.adjustmentReason}</span>
              </div>
            )}

            {rec.adjustmentActivityId && (
              <Button
                variant="light"
                color="orange"
                size="xs"
                mt="xs"
                onClick={() => current.push(ActivityDetailOverlay, { activityId: rec.adjustmentActivityId! })}>
                View Adjustment Activity Details
              </Button>
            )}
          </div>
        )}

        {/* 5. Actions */}
        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onBack}>
            Back to List
          </Button>

          {canCorrect && (
            <Button
              color="violet"
              variant="light"
              size="md"
              className={classes.actionBtn}
              leftSection={<ArrowCounterClockwiseIcon size={16} weight="bold" />}
              onClick={onStartCorrection}>
              Correct Reconciliation
            </Button>
          )}
        </div>
      </Stack>
    </div>
  );
}
