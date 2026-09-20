import { Alert, Button, Select, Skeleton, Stack, Text } from '@mantine/core';
import { ChartLineUpIcon, PlusIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { $api } from '@/api/client';
import { TradeOverlay } from '../record-trade/record-trade';
import { PositionItem } from './position-item';
import classes from './position-list.module.css';

interface PositionListProps {
  initialAccountId?: string;
  hideFilter?: boolean;
}

export function PositionList({ initialAccountId, hideFilter = false }: PositionListProps) {
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(initialAccountId ?? null);
  const [page, setPage] = useState(0);

  // Accounts for filter
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: { query: { includeArchived: false } }
  });

  const brokerageAccounts = useMemo(() => {
    return (accountsQuery.data ?? []).filter((acc) => acc.kind === 'BROKERAGE' && acc.trackingMode === 'FULL_LEDGER');
  }, [accountsQuery.data]);

  // Positions query
  const positionsQuery = $api.useQuery('get', '/api/v1/investing/positions', {
    params: {
      query: {
        accountId: selectedAccountId || undefined,
        pageable: { page, size: 50 }
      }
    }
  });

  const positions = positionsQuery.data?.items ?? [];
  const hasNext = positionsQuery.data?.hasNext ?? false;

  const handleOpenTrade = () => {
    TradeOverlay.open({
      defaultAccountId: selectedAccountId || undefined,
      defaultSide: 'BUY'
    });
  };

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

      {/* Loading Skeletons */}
      {positionsQuery.isLoading ? (
        <Stack gap="sm">
          <Skeleton height={60} radius="md" />
          <Skeleton height={60} radius="md" />
          <Skeleton height={60} radius="md" />
        </Stack>
      ) : positionsQuery.isError ? (
        <Alert icon={<WarningCircleIcon size={20} />} title="Failed to load positions" color="red" variant="light">
          <Text size="sm" mb="xs">
            Could not load your portfolio positions. Please try again.
          </Text>
          <Button size="xs" variant="outline" color="red" onClick={() => positionsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      ) : positions.length === 0 ? (
        <div className={classes.emptyState}>
          <div className={classes.emptyIcon}>
            <ChartLineUpIcon size={24} />
          </div>
          <Stack gap={4} align="center">
            <Text fw={700} size="md">
              No Investments Yet
            </Text>
            <Text size="xs" c="dimmed" maw={320}>
              You don't own any stocks or funds yet. Buy your first stock to start tracking your investments.
            </Text>
          </Stack>
          <Button
            size="sm"
            variant="filled"
            color="brand"
            leftSection={<PlusIcon size={16} weight="bold" />}
            onClick={handleOpenTrade}
            style={{ minHeight: 40 }}>
            Buy First Stock
          </Button>
        </div>
      ) : (
        <div className={classes.positionsList}>
          {positions.map((pos) => (
            <PositionItem key={`${pos.accountId}-${pos.instrumentId}`} position={pos} />
          ))}

          {hasNext && (
            <Button variant="subtle" size="sm" onClick={() => setPage((p) => p + 1)} loading={positionsQuery.isFetching}>
              Load More Positions
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
