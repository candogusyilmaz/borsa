import { DonutChart } from '@mantine/charts';
import { Box, Card, type CardProps, Divider, Group, rem, Stack, Text, useMatches } from '@mantine/core';
import { useParams } from '@tanstack/react-router';
import { useMemo, useState } from 'react';
import { $api } from '~/api/openapi';
import type { paths } from '~/api/schema';
import Currency from '~/components/Currency';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { format } from '~/lib/format';

const COLORS = [
  { id: 0, color: '#0066ff' },
  { id: 1, color: '#e85dff' },
  { id: 2, color: '#ffb800' }
];

type Positions = paths['/api/positions']['get']['responses'][200]['content']['*/*'];

export function BalanceCard() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = $api.useQuery('get', '/api/positions', {
    params: {
      query: {
        portfolioId: Number(portfolioId!)
      }
    }
  });

  if (status === 'pending') {
    return (
      <BalanceContainer miw={275} mih={325}>
        <LoadingView />
      </BalanceContainer>
    );
  }

  if (status === 'error') {
    return (
      <BalanceContainer miw={275} mih={325} style={{ borderColor: 'var(--mantine-color-red-5)' }}>
        <ErrorView />
      </BalanceContainer>
    );
  }

  return <Inner positions={data} />;
}

function BalanceContainer({ children, ...props }: CardProps) {
  return (
    <Card withBorder {...props}>
      {children}
    </Card>
  );
}

function Inner({ positions }: { positions: Positions }) {
  const chartSize = useMatches({ base: 200, sm: 230 });
  const [activeSegment, setActiveSegment] = useState<{ name: string; value: number; percent: number } | null>(null);

  // --- Derived State (Calculations) ---
  const stats = useMemo(() => {
    const totalValue = positions.reduce((acc, p) => acc + p.total, 0);
    const totalCost = positions.reduce((acc, p) => acc + p.avgCost * p.quantity, 0);
    const totalProfit = totalValue - totalCost;
    const totalProfitPercentage = totalCost > 0 ? (totalProfit / totalCost) * 100 : 0;

    const sorted = [...positions]
      .map((p) => ({
        symbol: p.instrument.symbol,
        value: p.total,
        quantity: p.quantity,
        averagePrice: p.avgCost,
        profit: p.total - p.avgCost * p.quantity,
        profitPercentage: (p.total / (p.avgCost * p.quantity) - 1) * 100
      }))
      .sort((a, b) => b.value - a.value);

    const top3 = sorted.slice(0, 3);
    const otherValue = sorted.slice(3).reduce((acc, p) => acc + p.value, 0);

    return { totalValue, totalProfit, totalProfitPercentage, top3, otherValue };
  }, [positions]);

  const pieData = [
    ...stats.top3.map((stock, idx) => ({
      name: stock.symbol,
      value: stock.value,
      color: COLORS[idx]?.color ?? 'gray'
    })),
    ...(stats.otherValue > 0 ? [{ name: 'Other', value: stats.otherValue, color: 'lightgray' }] : [])
  ];

  return (
    <BalanceContainer miw={275} p={'lg'}>
      <Group justify="center">
        <Box pos="relative">
          <DonutChart
            data={pieData}
            size={chartSize}
            thickness={18}
            paddingAngle={3}
            pieProps={{
              cornerRadius: 5,
              onMouseEnter: (s) => setActiveSegment(s),
              onMouseLeave: () => setActiveSegment(null),
              minAngle: 10
            }}
            withTooltip={false}
          />
          <Stack style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', textAlign: 'center' }} gap={4}>
            {!activeSegment ? (
              <>
                <Currency fz={rem(22)} fw={500}>
                  {stats.totalValue}
                </Currency>
                {positions.length !== 0 && (
                  <Text fz="sm" fw={700} c={stats.totalProfit >= 0 ? 'teal' : 'red'}>
                    {`${stats.totalProfit > 0 ? '+' : ''}${format.toCurrency(stats.totalProfit)}`}
                  </Text>
                )}
              </>
            ) : (
              <>
                <Text size={rem(22)} fw={700}>
                  {activeSegment.name}
                </Text>
                <Text size={rem(18)} fw={500} c="teal">
                  {format.toLocalePercentage(activeSegment.percent * 100)}
                </Text>
              </>
            )}
          </Stack>
        </Box>
      </Group>

      {stats.top3.length > 0 && (
        <Stack justify="center" gap="sm" mt="xl">
          <Text c="dimmed" ta="center" size="sm" fw={700}>
            Top Holdings
          </Text>
          {stats.top3.map((stock, idx) => (
            <Stack key={stock.symbol} gap="sm" justify="center">
              <Box>
                <Group justify="space-between">
                  <Text>{stock.symbol}</Text>
                  <Currency fw={500}>{stock.value}</Currency>
                </Group>
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    {format.toHumanizedNumber(stock.quantity)} shares @ <Currency span>{stock.averagePrice}</Currency>
                  </Text>
                  <Text span size="xs" c={stock.profit >= 0 ? 'teal' : 'red'}>
                    {format.toLocalePercentage(stock.profitPercentage)}
                  </Text>
                </Group>
              </Box>
              {idx !== stats.top3.length - 1 && <Divider />}
            </Stack>
          ))}
        </Stack>
      )}
    </BalanceContainer>
  );
}
