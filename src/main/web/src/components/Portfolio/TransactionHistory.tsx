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
import { useParams } from '@tanstack/react-router';
import { useEffect, useMemo, useState } from 'react';
import { $api } from '~/api/openapi';
import type { paths } from '~/api/schema';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { useUndoTradeModalStore } from '~/components/Transaction/UndoTradeModal';
import { format } from '~/lib/format';

type TradeHistory = paths['/api/portfolios/{portfolioId}/trades']['get']['responses']['200']['content']['*/*'];
type Trade = paths['/api/portfolios/{portfolioId}/trades']['get']['responses']['200']['content']['*/*']['trades'][number];

const PAGE_SIZE_OPTIONS = [
  { value: '5', label: '5' },
  { value: '10', label: '10' },
  { value: '20', label: '20' },
  { value: '50', label: '50' }
];

export function TransactionHistory() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = $api.useQuery('get', '/api/portfolios/{portfolioId}/trades', {
    params: {
      path: {
        portfolioId: Number(portfolioId)
      }
    }
  });

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
        <Card shadow="sm" radius="md" withBorder style={{ background: 'var(--cv-card-bg)', backdropFilter: 'var(--cv-card-blur)' }}>
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
          <Text fw={900} size={rem(22)} lts="0.05em">
            Trades
          </Text>
        </Group>
      </Group>

      <Group gap="xs">
        <TextInput
          placeholder="Filter by symbol..."
          value={symbolFilter}
          onChange={(event) => setSymbolFilter(event.currentTarget.value)}
          leftSection={<IconSearch size={16} color="var(--cv-brand-500)" />}
          flex={1}
          miw={100}
          styles={{
            input: {
              backgroundColor: 'var(--cv-card-bg)',
              border: '1px solid var(--cv-border)',
              fontWeight: 600
            }
          }}
        />
        <SegmentedControl
          bd="1px solid var(--cv-border)"
          bg="var(--cv-card-bg)"
          value={typeFilter}
          onChange={(value) => setTypeFilter(value || '')}
          data={[
            { value: '', label: 'All' },
            { value: 'BUY', label: 'Buy' },
            { value: 'SELL', label: 'Sell' }
          ]}
          styles={{
            indicator: { backgroundImage: 'var(--cv-gradient-brand)' },
            label: { fontWeight: 700, fontSize: rem(11) }
          }}
        />
        <SegmentedControl
          styles={{
            root: { background: 'transparent' },
            label: { fontWeight: 700, fontSize: rem(10), fontFamily: 'var(--mantine-font-family-monospace)' }
          }}
          value={pageSize.toString()}
          onChange={(value) => setPageSize(Number(value))}
          data={PAGE_SIZE_OPTIONS}
        />
      </Group>

      <Stack gap="xs">
        {currentTrades.map((trade) => (
          <Card
            key={trade.createdAt}
            shadow="var(--cv-card-shadow)"
            radius="md"
            p="sm"
            px="lg"
            withBorder
            pos="relative"
            style={{
              overflow: 'visible',
              background: 'var(--cv-card-bg)',
              backdropFilter: 'var(--cv-card-blur)',
              borderColor: 'var(--cv-border)',
              transition: 'border-color 0.2s ease'
            }}>
            <TradeCard trade={trade} />
            <UndoTradeButton trade={trade} />
          </Card>
        ))}
        {currentTrades.length === 0 && (
          <Card shadow="sm" radius="md" withBorder style={{ background: 'var(--cv-card-bg)', borderColor: 'var(--cv-border)' }}>
            <Text c="dimmed" size="xs" fw={500} ta="center">
              No trades match the current filters
            </Text>
          </Card>
        )}
      </Stack>
      {totalPages > 1 && (
        <Group justify="right" gap="xs">
          <ActionIcon variant="subtle" color="gray" disabled={currentPage === 1} onClick={handlePrevPage}>
            <IconChevronLeft size={18} />
          </ActionIcon>
          <Text size="xs" fw={800} ff="var(--mantine-font-family-monospace)" c="dimmed">
            {currentPage} <span style={{ opacity: 0.4 }}>/</span> {totalPages}
          </Text>
          <ActionIcon variant="subtle" color="gray" disabled={currentPage === totalPages} onClick={handleNextPage}>
            <IconChevronRight size={18} />
          </ActionIcon>
        </Group>
      )}
    </Stack>
  );
}

function DesktopCardContent({ trade }: { trade: Trade }) {
  const isBuy = trade.type === 'BUY';
  const isProfit = (trade.profit ?? 0) >= 0;
  const color = isBuy ? 'var(--cv-brand-500)' : isProfit ? 'var(--cv-profit)' : 'var(--cv-loss)';

  return (
    <Group justify="space-between" align="center" ta="right">
      <Group gap={4} align="center">
        <ThemeIcon ml={-6} variant="transparent" size="xl" radius="md" c={color}>
          {isBuy ? (
            <IconCirclePlus size={22} stroke={2.5} />
          ) : isProfit ? (
            <IconTrendingUp3 size={22} stroke={2.5} />
          ) : (
            <IconTrendingDown3 size={22} stroke={2.5} />
          )}
        </ThemeIcon>
        <Stack gap={2}>
          <Group gap="xs" align="center" ml={3}>
            <Text size="lg" fw={800}>
              {trade.symbol}
            </Text>
            <Badge
              size="xs"
              variant="filled"
              color={isBuy ? 'brand' : isProfit ? 'profit' : 'loss'}
              styles={{ root: { height: 16, padding: '0 6px' }, label: { fontSize: 9, fontWeight: 900 } }}>
              {trade.type}
            </Badge>
          </Group>
          <Group gap={4} align="center" ml={4}>
            <IconCalendar size={12} color="var(--cv-text-xmuted)" />
            <Text size="xs" c="dimmed" fw={500}>
              {format.toFullDateTime(new Date(trade.date))}
            </Text>
          </Group>
        </Stack>
      </Group>
      <Group align="center" miw={rem(300)}>
        <Stack gap={2} miw={rem(150)}>
          <Group gap={4} align="center" justify="flex-end">
            <IconChartBar size={14} color="var(--cv-text-xmuted)" />
            <Text fw={700} fz="sm" ff="var(--mantine-font-family-monospace)">{`${format.toHumanizedNumber(trade.quantity)}`}</Text>
            <Text size="xs" c="dimmed" fw={500}>
              shares
            </Text>
          </Group>
          <Text size="xs" c="dimmed" fw={500} ff="var(--mantine-font-family-monospace)">
            @ {format.toCurrency(trade.price)}
          </Text>
        </Stack>
        <Stack gap={2} ml="auto">
          <Text fw={800} fz="md" ff="var(--mantine-font-family-monospace)">
            {format.toCurrency(trade.total)}
          </Text>
          {!isBuy && (
            <Text size="xs" fw={700} ff="var(--mantine-font-family-monospace)" style={{ color }}>
              {`${format.toCurrency(trade.profit!)} (${format.toLocalePercentage(trade.returnPercentage!)})`}
            </Text>
          )}
        </Stack>
      </Group>
    </Group>
  );
}

function MobileCardContent({ trade }: { trade: Trade }) {
  const isBuy = trade.type === 'BUY';
  const isProfit = (trade.profit ?? 0) >= 0;
  const color = isBuy ? 'var(--cv-brand-500)' : isProfit ? 'var(--cv-profit)' : 'var(--cv-loss)';

  return (
    <Stack gap={4}>
      <Group gap="xs" justify="space-between" align="center">
        <Group gap={6}>
          <ThemeIcon size="sm" variant="transparent" c={color}>
            {isBuy ? (
              <IconCirclePlus size={18} stroke={2.5} />
            ) : isProfit ? (
              <IconTrendingUp3 size={18} stroke={2.5} />
            ) : (
              <IconTrendingDown3 size={18} stroke={2.5} />
            )}
          </ThemeIcon>
          <Text size="md" fw={800} ff="var(--mantine-font-family-monospace)">
            {trade.symbol}
          </Text>
        </Group>
        <Badge size="xs" variant="light" color={isBuy ? 'brand' : isProfit ? 'profit' : 'loss'}>
          {trade.type}
        </Badge>
      </Group>

      <Group gap={4} align="center" justify="space-between">
        <Group gap={3} align="center">
          <Text size="xs" c="dimmed" fw={600} ff="var(--mantine-font-family-monospace)">
            {`${format.toHumanizedNumber(trade.quantity)}`}
            <span style={{ fontWeight: 400, marginLeft: 2 }}>sh</span>
          </Text>
          <Text size="xs" c="dimmed" fw={400}>
            @
          </Text>
          <Text size="xs" c="dimmed" fw={600} ff="var(--mantine-font-family-monospace)">
            {format.toCurrency(trade.price)}
          </Text>
        </Group>
        <Text size="sm" fw={800} ff="var(--mantine-font-family-monospace)">
          {format.toCurrency(trade.total)}
        </Text>
      </Group>
      {!isBuy && (
        <Text ta="right" size="xs" fw={700} ff="var(--mantine-font-family-monospace)" style={{ color }}>
          {`${format.toCurrency(trade.profit!)} (${format.toLocalePercentage(trade.returnPercentage!)})`}
        </Text>
      )}
    </Stack>
  );
}

function UndoTradeButton({ trade }: { trade: Trade }) {
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
