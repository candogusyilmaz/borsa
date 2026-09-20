import { Alert, Badge, Button, Group, Skeleton, Stack, Text } from '@mantine/core';
import { ArrowCounterClockwiseIcon, CalendarCheckIcon, DatabaseIcon, TrendUpIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { ReverseActivityOverlay } from '@/features/account/components/reverse-activity/reverse-activity';
import {
  formatCurrency,
  formatDateTime,
  getPolicyDecisionBadgeColor,
  getPolicyDecisionLabel,
  getPostingRoleLabel,
  getRecordingModeLabel,
  getSecurityPostingRoleLabel
} from '@/features/account/utils/account-formatters';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import { formatQuantity, getCalculationPolicyLabel, getTradeSideBadgeColor, getTradeSideLabel } from '../../utils/investing-formatters';
import classes from './trade-detail.module.css';

export interface TradeDetailProps {
  activityId: string;
}

export function TradeDetail({ activityId }: TradeDetailProps) {
  const current = useCurrentOverlay();

  const tradeQuery = $api.useQuery('get', '/api/v1/trades/{activityId}', {
    params: { path: { activityId } }
  });

  const trade = tradeQuery.data;

  const handleClose = () => {
    current.dismiss('cancelled');
  };

  const isReversed = Boolean(trade?.reversalActivityId);

  return (
    <>
      {tradeQuery.isLoading ? (
        <Stack gap="md" p="md">
          <Skeleton height={40} radius="sm" />
          <Skeleton height={120} radius="md" />
          <Skeleton height={140} radius="md" />
        </Stack>
      ) : tradeQuery.isError || !trade ? (
        <Alert icon={<WarningCircleIcon size={20} />} color="red" variant="light" m="md">
          Could not load details for this trade. It may have been removed or does not exist.
        </Alert>
      ) : (
        <div className={classes.container}>
          {/* 1. Header Info */}
          <div className={classes.headerSection}>
            <div className={classes.titleRow}>
              <Group gap="xs">
                <Badge size="lg" variant="filled" color={getTradeSideBadgeColor(trade.side)}>
                  {getTradeSideLabel(trade.side)}
                </Badge>
                <Text fw={700} size="lg">
                  {trade.instrumentSymbol}
                </Text>
                <Badge size="sm" variant="outline" color="gray">
                  {trade.instrumentType}
                </Badge>
              </Group>

              <Group gap="xs">
                <Badge color="gray" variant="outline" size="sm">
                  {getRecordingModeLabel(trade.recordingMode)}
                </Badge>
                {trade.policyDecision !== 'NOT_APPLICABLE' && (
                  <Badge color={getPolicyDecisionBadgeColor(trade.policyDecision)} variant="light" size="sm">
                    {getPolicyDecisionLabel(trade.policyDecision)}
                  </Badge>
                )}
              </Group>
            </div>

            <Text size="sm" c="dimmed">
              {trade.instrumentName} &bull; Account: {trade.accountName}
            </Text>
          </div>

          {/* 2. Reversal Banner */}
          {isReversed && (
            <Alert icon={<ArrowCounterClockwiseIcon size={18} weight="bold" />} color="violet" variant="light">
              <Text size="sm" fw={600}>
                This trade has been undone by a correction entry.
              </Text>
              {trade.reversalReason && (
                <Text size="xs" mt={2}>
                  Reason: {trade.reversalReason}
                </Text>
              )}
            </Alert>
          )}

          {/* 3. Financial Metrics Grid */}
          <div className={classes.financialGrid}>
            <div className={classes.metricCard}>
              <span className={classes.metricLabel}>Shares</span>
              <span className={classes.metricValue}>{formatQuantity(trade.quantity)} units</span>
            </div>

            <div className={classes.metricCard}>
              <span className={classes.metricLabel}>Price per Share</span>
              <span className={classes.metricValue}>{formatCurrency(trade.unitPrice, trade.currency)}</span>
            </div>

            <div className={classes.metricCard}>
              <span className={classes.metricLabel}>Total Shares Value</span>
              <span className={classes.metricValue}>{formatCurrency(trade.grossAmount, trade.currency)}</span>
            </div>

            <div className={classes.metricCard}>
              <span className={classes.metricLabel}>Trading Fee</span>
              <span className={classes.metricValue}>
                {Number.parseFloat(trade.commissionAmount) > 0 ? formatCurrency(trade.commissionAmount, trade.currency) : 'Free (0.00)'}
              </span>
            </div>

            <div className={classes.metricCard} style={{ gridColumn: 'span 2' }}>
              <span className={classes.metricLabel}>Total Cash Impact</span>
              <span
                className={classes.metricValue}
                style={{
                  color: trade.cashDelta.startsWith('-') ? 'var(--mantine-color-red-filled)' : 'var(--mantine-color-teal-filled)'
                }}>
                {trade.cashDelta.startsWith('-') ? '' : '+'}
                {formatCurrency(trade.cashDelta, trade.currency)}
              </span>
            </div>
          </div>

          {/* 4. Timestamp & Economic Order */}
          <div className={classes.timeGrid}>
            <div className={classes.metricCard}>
              <Group gap={6} mb={4}>
                <CalendarCheckIcon size={16} weight="bold" color="var(--mantine-primary-color-filled)" />
                <span className={classes.metricLabel}>Trade Date &amp; Time</span>
              </Group>
              <span className={classes.metricValue} style={{ fontSize: 'var(--mantine-font-size-sm)' }}>
                {formatDateTime(trade.effectiveAt)}
              </span>
              <Text size="xs" c="dimmed">
                Same-day Order: {trade.economicSequence}
              </Text>
            </div>

            <div className={classes.metricCard}>
              <Group gap={6} mb={4}>
                <DatabaseIcon size={16} weight="bold" color="var(--mantine-color-gray-6)" />
                <span className={classes.metricLabel}>Saved In App</span>
              </Group>
              <span className={classes.metricValue} style={{ fontSize: 'var(--mantine-font-size-sm)' }}>
                {formatDateTime(trade.recordedAt)}
              </span>
              <Text size="xs" c="dimmed">
                Cost Rule: {getCalculationPolicyLabel(trade.calculationPolicy)}
              </Text>
            </div>
          </div>

          {/* 5. Postings Breakdown */}
          <Stack gap="xs">
            <Text size="sm" fw={700}>
              Transaction Breakdown
            </Text>

            <div className={classes.postingsList}>
              {/* Security Posting */}
              {trade.securityPosting && (
                <div className={classes.postingRow}>
                  <div>
                    <Badge size="xs" variant="light" color={trade.side === 'BUY' ? 'teal' : 'indigo'}>
                      {getSecurityPostingRoleLabel(trade.securityPosting.role)}
                    </Badge>
                    <Text size="xs" c="dimmed" mt={2}>
                      Shares &bull; {trade.instrumentSymbol}
                    </Text>
                  </div>
                  <Text
                    size="sm"
                    fw={700}
                    c={trade.securityPosting.quantityDelta.startsWith('-') ? 'indigo.6' : 'teal.6'}
                    style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {trade.securityPosting.quantityDelta.startsWith('-') ? '' : '+'}
                    {formatQuantity(trade.securityPosting.quantityDelta)} units
                  </Text>
                </div>
              )}

              {/* Cash Postings */}
              {trade.cashPostings.map((cashPosting) => {
                const isPositive = !cashPosting.amount.startsWith('-');
                return (
                  <div key={`${cashPosting.pocketId}-${cashPosting.role}`} className={classes.postingRow}>
                    <div>
                      <Badge size="xs" variant="light" color={isPositive ? 'teal' : 'orange'}>
                        {getPostingRoleLabel(cashPosting.role)}
                      </Badge>
                      <Text size="xs" c="dimmed" mt={2}>
                        {cashPosting.currency} Cash Account
                      </Text>
                    </div>
                    <Text size="sm" fw={700} c={isPositive ? 'teal.6' : 'red.6'} style={{ fontVariantNumeric: 'tabular-nums' }}>
                      {isPositive ? '+' : ''}
                      {formatCurrency(cashPosting.amount, cashPosting.currency)}
                    </Text>
                  </div>
                );
              })}
            </div>
          </Stack>

          {/* Reference ID */}
          <Text size="xs" c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
            Trade ID: {trade.id}
          </Text>

          {/* Actions */}
          <div className={classes.actions}>
            <Button variant="default" size="md" onClick={handleClose} className={classes.actionBtn}>
              Close
            </Button>
            {!isReversed && (
              <Button
                color="violet"
                variant="light"
                size="md"
                className={classes.actionBtn}
                leftSection={<ArrowCounterClockwiseIcon size={16} weight="bold" />}
                onClick={() => {
                  handleClose();
                  ReverseActivityOverlay.open({ activityId: trade.id });
                }}>
                Undo Trade
              </Button>
            )}
          </div>
        </div>
      )}
    </>
  );
}

export const TradeDetailOverlay = registerOverlay(TradeDetail, {
  name: 'trade-detail',
  title: (
    <Group gap="xs">
      <TrendUpIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Trade Details
      </Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'lg'
});
