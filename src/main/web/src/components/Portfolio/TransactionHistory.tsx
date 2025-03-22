import { ActionIcon, Badge, Card, Group, Stack, Text, ThemeIcon, Tooltip, rem, useMatches } from '@mantine/core';
import {
  IconArrowBack,
  IconCalendar,
  IconChartBar,
  IconChevronLeft,
  IconChevronRight,
  IconCirclePlus,
  IconTrendingDown3,
  IconTrendingUp3
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { queries } from '~/api';
import type { TradeHistory, TradeHistoryTrade } from '~/api/queries/types';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { useUndoTradeModalStore } from '~/components/Transaction/UndoTradeModal';
import { format } from '~/lib/format';

const PAGE_SIZE = 5;

export function TransactionHistory() {
  const { data, status } = useQuery(queries.trades.fetchAll());

  if (status === 'pending') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Transaction History
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
          Transaction History
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
          Transaction History
        </Text>
        <Card shadow="sm" radius="md" withBorder>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            There are no transactions to list
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

  const totalPages = Math.ceil(data.trades.length / PAGE_SIZE);

  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const endIndex = startIndex + PAGE_SIZE;

  const currentTrades = data.trades.slice(startIndex, endIndex);

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
            Transaction History
          </Text>
          {data.trades.length > 0 && (
            <Badge variant="light" color="blue.2" style={{ flex: 1, minWidth: 60 }}>
              {data.trades.length} trade{data.trades.length === 1 ? '' : 's'}
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

  if (!trade.latest) return <></>;

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
