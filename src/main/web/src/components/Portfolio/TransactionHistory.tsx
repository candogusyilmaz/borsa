import {
  ActionIcon,
  Badge,
  Card,
  Group,
  rem,
  SegmentedControl,
  Stack,
  Text,
  TextInput,
  ThemeIcon,
  Tooltip,
  useMatches
} from '@mantine/core';
import {
  IconArrowBack,
  IconCalendar,
  IconChartBar,
  IconChevronLeft,
  IconChevronRight,
  IconCirclePlus,
  IconSearch,
  IconTrendingDown3,
  IconTrendingUp3
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useEffect, useMemo, useState } from 'react';
import { queries } from '~/api';
import type { TradeHistory, TradeHistoryTrade } from '~/api/queries/types';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { useUndoTradeModalStore } from '~/components/Transaction/UndoTradeModal';
import { format } from '~/lib/format';

const PAGE_SIZE_OPTIONS = [
  { value: '5', label: '5' },
  { value: '10', label: '10' },
  { value: '20', label: '20' },
  { value: '50', label: '50' }
];

export function TransactionHistory() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = useQuery(queries.trades.fetchAll({ portfolioId: Number(portfolioId) }));

  if (status === 'pending') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Trade History
        </Text>
        <Card shadow="sm" radius="md" p="lg" withBorder>
          <LoadingView />
        </Card>
      </Stack>
    );
  }

  if (status === 'error') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Trade History
        </Text>
        <Card shadow="sm" radius="md" p="lg" withBorder style={{ borderColor: 'var(--mantine-color-red-5)' }}>
          <ErrorView />
        </Card>
      </Stack>
    );
  }

  if (data.trades.length === 0) {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Trade History
        </Text>
        <Card shadow="sm" radius="md" withBorder>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            There are no trades to list
          </Text>
        </Card>
      </Stack>
    );
  }

  return <Inner data={data} />;
}

function Inner({ data }: { data: TradeHistory }) {
  const TradeCard = useMatches({
    base: MobileCardContent,
    sm: DesktopCardContent
  });
  const [currentPage, setCurrentPage] = useState(1);
  const [symbolFilter, setSymbolFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('');
  const [pageSize, setPageSize] = useState(5);

  // Filter trades based on symbol and type
  const filteredTrades = useMemo(() => {
    return data.trades.filter((trade) => {
      const symbolMatch = symbolFilter === '' || trade.symbol.toLowerCase().includes(symbolFilter.toLowerCase());
      const typeMatch = typeFilter === '' || trade.type === typeFilter;
      return symbolMatch && typeMatch;
    });
  }, [data.trades, symbolFilter, typeFilter]);

  const totalPages = Math.ceil(filteredTrades.length / pageSize);

  // Reset to first page when filters change
  useEffect(() => {
    if (symbolFilter !== '' || typeFilter !== '') {
      setCurrentPage(1);
    }
  }, [symbolFilter, typeFilter]);

  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;

  const currentTrades = filteredTrades.slice(startIndex, endIndex);

  const handleNextPage = () => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1);
    }
  };

  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1);
    }
  };

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Group>
          <Text fw={700} size={rem(22)}>
            Trade History
          </Text>
          {filteredTrades.length > 0 && (
            <Badge variant="light" color="blue.2" style={{ flex: 1, minWidth: 60 }}>
              {filteredTrades.length} trade{filteredTrades.length === 1 ? '' : 's'}
              {filteredTrades.length !== data.trades.length && ` of ${data.trades.length}`}
            </Badge>
          )}
        </Group>
        {totalPages > 1 && (
          <Group justify="space-between">
            <ActionIcon variant="transparent" c={currentPage === 1 ? 'dimmed' : 'white'} onClick={handlePrevPage}>
              <IconChevronLeft />
            </ActionIcon>
            <Text size="sm" fw={500}>
              {`${currentPage}/${totalPages}`}
            </Text>
            <ActionIcon variant="transparent" c={currentPage === totalPages ? 'dimmed' : 'white'} onClick={handleNextPage}>
              <IconChevronRight />
            </ActionIcon>
          </Group>
        )}
      </Group>

      <Group gap="xs">
        <SegmentedControl
          bd="1px solid var(--mantine-color-dark-4)"
          value={typeFilter}
          onChange={(value) => setTypeFilter(value || '')}
          data={[
            { value: '', label: 'All' },
            { value: 'BUY', label: 'Buy' },
            { value: 'SELL', label: 'Sell' }
          ]}
        />
        <TextInput
          placeholder="Filter by symbol..."
          value={symbolFilter}
          onChange={(event) => setSymbolFilter(event.currentTarget.value)}
          leftSection={<IconSearch size={16} />}
          flex={1}
          miw={100}
        />
        <SegmentedControl
          styles={{
            root: {
              background: 'transparent'
            }
          }}
          value={pageSize.toString()}
          onChange={(value) => setPageSize(Number(value))}
          data={PAGE_SIZE_OPTIONS}
        />
      </Group>

      <Stack gap="sm">
        {currentTrades.map((trade) => (
          <Card
            key={trade.createdAt}
            shadow="sm"
            radius="md"
            p="xs"
            px="lg"
            withBorder
            pos="relative"
            style={{
              overflow: 'visible'
            }}>
            <TradeCard trade={trade} />
            <UndoTradeButton trade={trade} />
          </Card>
        ))}
        {currentTrades.length === 0 && (
          <Card shadow="sm" radius="md" withBorder>
            <Text c="dimmed" size="xs" fw={600} ta="center">
              No trades match the current filters
            </Text>
          </Card>
        )}
      </Stack>
    </Stack>
  );
}

function DesktopCardContent({ trade }: { trade: TradeHistoryTrade }) {
  return (
    <Group justify="space-between" align="center" ta="right">
      <Group align="center">
        <ThemeIcon variant="transparent" c={trade.type === 'BUY' ? 'blue' : trade.profit >= 0 ? 'green' : 'red'}>
          {trade.type === 'BUY' ? <IconCirclePlus /> : trade.profit >= 0 ? <IconTrendingUp3 /> : <IconTrendingDown3 />}
        </ThemeIcon>
        <Stack gap={2}>
          <Group gap="xs" align="center" ml={3}>
            <Text size="lg" fw={600}>
              {trade.symbol}
            </Text>
            <Badge size="xs" variant="light" color={trade.type === 'BUY' ? 'blue' : trade.profit >= 0 ? 'green' : 'red'}>
              {trade.type}
            </Badge>
          </Group>
          <Group gap={4} align="center">
            <ThemeIcon size="xs" variant="transparent" c="dimmed">
              <IconCalendar style={{ width: '100%', height: '100%' }} />
            </ThemeIcon>
            <Text size="xs" c="dimmed">
              {format.toFullDateTime(new Date(trade.date))}
            </Text>
          </Group>
        </Stack>
      </Group>
      <Group align="center" miw={rem(300)}>
        <Stack gap={2} miw={rem(150)}>
          <Group gap={4} align="center" justify="flex-end">
            <ThemeIcon size="xs" variant="transparent" c="dimmed">
              <IconChartBar />
            </ThemeIcon>
            <Text fw={500}>{`${format.toHumanizedNumber(trade.quantity)} share${trade.quantity === 1 ? '' : 's'}`}</Text>
          </Group>
          <Text size="xs" c="dimmed">
            @ {format.toCurrency(trade.price)}
          </Text>
        </Stack>
        <Stack gap={2} ml="auto">
          <Text fw={600}>{format.toCurrency(trade.total)}</Text>
          {trade.type === 'SELL' && (
            <Text size="xs" c={trade.returnPercentage >= 0 ? 'teal' : 'red'}>
              {`${format.toCurrency(trade.profit)} (${format.toLocalePercentage(trade.returnPercentage)})`}
            </Text>
          )}
        </Stack>
      </Group>
    </Group>
  );
}

function MobileCardContent({ trade }: { trade: TradeHistoryTrade }) {
  return (
    <Stack gap={4}>
      <Group gap="xs" justify="space-between" align="center">
        <Group gap={4}>
          <ThemeIcon size="xs" variant="transparent" c={trade.type === 'BUY' ? 'blue' : trade.profit >= 0 ? 'green' : 'red'}>
            {trade.type === 'BUY' ? (
              <IconCirclePlus style={{ width: '100%' }} />
            ) : trade.profit >= 0 ? (
              <IconTrendingUp3 />
            ) : (
              <IconTrendingDown3 />
            )}
          </ThemeIcon>
          <Text size="lg" fw={600}>
            {trade.symbol}
          </Text>
        </Group>
        <Badge size="xs" variant="light" color={trade.type === 'BUY' ? 'blue' : trade.profit >= 0 ? 'green' : 'red'}>
          {trade.type}
        </Badge>
      </Group>

      <Group gap={4} align="center" justify="space-between">
        <Group gap={3} align="center">
          <ThemeIcon size="xs" variant="transparent" c="dimmed">
            <IconChartBar />
          </ThemeIcon>
          <Text size="xs" c="dimmed">
            {`${format.toHumanizedNumber(trade.quantity)} share`}
          </Text>
          <Text size="xs" c="dimmed">
            @ {format.toCurrency(trade.price)}
          </Text>
        </Group>
        <Text size="xs" fw={600}>
          {format.toCurrency(trade.total)}
        </Text>
      </Group>
      {trade.type === 'SELL' && (
        <Text ta="right" size="xs" c={trade.returnPercentage >= 0 ? 'teal' : 'red'}>
          {`${format.toCurrency(trade.profit)} (${format.toLocalePercentage(trade.returnPercentage)})`}
        </Text>
      )}
    </Stack>
  );
}

function UndoTradeButton({ trade }: { trade: TradeHistoryTrade }) {
  const openUndoModal = useUndoTradeModalStore((s) => s.open);

  if (!trade.latest) return;

  return (
    <Tooltip label="Undo trade" openDelay={500} transitionProps={{ duration: 300 }} fz={12}>
      <ActionIcon
        variant="subtle"
        color="gray.6"
        pos="absolute"
        size="sm"
        right={0}
        style={{
          top: '50%',
          transform: 'translateY(-50%) translateX(50%)'
        }}
        aria-label="Undo the action"
        onClick={() => openUndoModal(trade)}>
        <IconArrowBack style={{ width: '85%' }} />
      </ActionIcon>
    </Tooltip>
  );
}
