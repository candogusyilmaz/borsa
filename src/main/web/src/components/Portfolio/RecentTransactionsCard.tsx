import { ActionIcon, Badge, Card, Group, Stack, Text, ThemeIcon, rem } from '@mantine/core';
import { IconCalendar, IconChartBar, IconChevronLeft, IconChevronRight, IconCircleArrowDown, IconCircleArrowUp } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { queries } from '~/api';
import type { TradeHistory } from '~/api/queries/types';
import { format } from '~/lib/format';
import { ErrorView } from '../ErrorView';
import { LoadingView } from '../LoadingView';

const PAGE_SIZE = 5;

export function RecentTransactionsCard() {
  const { data, status } = useQuery(queries.member.tradeHistory());

  if (status === 'pending') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Recent Transactions
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
          Recent Transactions
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
          Recent Transactions
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
            Recent Transactions
          </Text>
          {data.trades.length > 0 && (
            <Badge variant="light" color="blue.2">
              {data.trades.length} trades
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
        {currentTrades.map((trade, idx) => (
          <Card
            // biome-ignore lint/suspicious/noArrayIndexKey: <explanation>
            key={idx}
            shadow="sm"
            radius="md"
            p="xs"
            px="lg"
            withBorder>
            <Group justify="space-between" align="center" ta="right">
              <Group align="center">
                <ThemeIcon variant="transparent" c={trade.type === 'BUY' ? 'red' : 'green'}>
                  {trade.type === 'BUY' ? <IconCircleArrowUp /> : <IconCircleArrowDown />}
                </ThemeIcon>
                <Stack gap={2}>
                  <Group gap="xs" align="center" ml={3}>
                    <Text size="lg" fw={600}>
                      {trade.symbol}
                    </Text>
                    <Badge size="xs" variant="light" color={trade.type === 'BUY' ? 'red' : 'green'}>
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
                <Stack gap={2}>
                  <Group gap={4} align="center">
                    <ThemeIcon size="xs" variant="transparent" c="dimmed">
                      <IconChartBar />
                    </ThemeIcon>
                    <Text fw={500}>{trade.quantity} shares</Text>
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
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}
