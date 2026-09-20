import { Alert, Badge, Button, Group, Select, Skeleton, Stack, Text } from '@mantine/core';
import { ClockCounterClockwiseIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { $api } from '@/api/client';
import { formatCurrency, formatDateTime } from '@/features/account/utils/account-formatters';
import { formatQuantity, getTradeSideBadgeColor, getTradeSideLabel } from '../../utils/investing-formatters';
import { TradeDetailOverlay } from '../trade-detail/trade-detail';
import classes from './trade-history.module.css';

interface TradeHistoryListProps {
  initialAccountId?: string;
  hideFilter?: boolean;
}

export function TradeHistoryList({ initialAccountId, hideFilter = false }: TradeHistoryListProps) {
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(initialAccountId ?? null);
  const [page, setPage] = useState(0);

  // Accounts for filter
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: { query: { includeArchived: false } }
  });

  const brokerageAccounts = useMemo(() => {
    return (accountsQuery.data ?? []).filter((acc) => acc.kind === 'BROKERAGE' && acc.trackingMode === 'FULL_LEDGER');
  }, [accountsQuery.data]);

  // Trades query
  const tradesQuery = $api.useQuery('get', '/api/v1/trades', {
    params: {
      query: {
        accountId: selectedAccountId || undefined,
        pageable: { page, size: 50, sort: ['effectiveAt,desc'] }
      }
    }
  });

  const trades = tradesQuery.data?.items ?? [];
  const hasNext = tradesQuery.data?.hasNext ?? false;

  return (
    <div className={classes.container}>
      {/* Optional Account Filter */}
      {!hideFilter && brokerageAccounts.length > 1 && (
        <div className={classes.filterBar}>
          <Text size="xs" c="dimmed" fw={600} tt="uppercase">
            Filter by Account
          </Text>
          <Select
            size="xs"
            placeholder="All Brokerage Accounts"
            clearable
            value={selectedAccountId}
            onChange={(val) => {
              setSelectedAccountId(val);
              setPage(0);
            }}
            data={brokerageAccounts.map((a) => ({
              value: a.id,
              label: `${a.name} (${a.currency})`
            }))}
            style={{ width: 220 }}
          />
        </div>
      )}

      {/* Skeletons */}
      {tradesQuery.isLoading ? (
        <Stack gap="sm">
          <Skeleton height={60} radius="md" />
          <Skeleton height={60} radius="md" />
          <Skeleton height={60} radius="md" />
        </Stack>
      ) : tradesQuery.isError ? (
        <Alert icon={<WarningCircleIcon size={20} />} title="Failed to load trades" color="red" variant="light">
          <Text size="sm" mb="xs">
            Could not load trade history. Please try again.
          </Text>
          <Button size="xs" variant="outline" color="red" onClick={() => tradesQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      ) : trades.length === 0 ? (
        <div className={classes.emptyState}>
          <div className={classes.emptyIcon}>
            <ClockCounterClockwiseIcon size={24} />
          </div>
          <Stack gap={4} align="center">
            <Text fw={700} size="md">
              No Trades Recorded
            </Text>
            <Text size="xs" c="dimmed" maw={300}>
              Executed buy and sell orders will be permanently audited and listed here.
            </Text>
          </Stack>
        </div>
      ) : (
        <div className={classes.tradesList}>
          {trades.map((trade) => {
            const isBuy = trade.side === 'BUY';
            const isReversed = Boolean(trade.reversalActivityId);

            return (
              <button
                key={trade.id}
                type="button"
                className={classes.tradeCard}
                onClick={() => TradeDetailOverlay.open({ activityId: trade.id })}
                aria-label={`View ${trade.instrumentSymbol} ${trade.side} trade details`}>
                <div className={classes.leftCol}>
                  <Group gap={6}>
                    <Badge size="sm" variant="filled" color={getTradeSideBadgeColor(trade.side)}>
                      {getTradeSideLabel(trade.side)}
                    </Badge>
                    <Text fw={700} size="sm">
                      {trade.instrumentSymbol}
                    </Text>
                    {isReversed && (
                      <Badge size="xs" variant="light" color="violet">
                        Reversed
                      </Badge>
                    )}
                  </Group>
                  <Text size="xs" c="dimmed">
                    {formatDateTime(trade.effectiveAt)} &bull; {trade.accountName}
                  </Text>
                </div>

                <div className={classes.rightCol}>
                  <Text size="sm" fw={700} c={isBuy ? 'red.6' : 'teal.6'} style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {trade.cashDelta.startsWith('-') ? '' : '+'}
                    {formatCurrency(trade.cashDelta, trade.currency)}
                  </Text>
                  <Text size="xs" c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {formatQuantity(trade.quantity)} @ {formatCurrency(trade.unitPrice, trade.currency)}
                  </Text>
                </div>
              </button>
            );
          })}

          {hasNext && (
            <Button variant="subtle" size="sm" onClick={() => setPage((p) => p + 1)} loading={tradesQuery.isFetching}>
              Load Older Trades
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
