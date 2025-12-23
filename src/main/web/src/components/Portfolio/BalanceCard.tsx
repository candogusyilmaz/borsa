import { DonutChart } from '@mantine/charts';
import { Box, Card, type CardProps, Divider, Group, rem, Stack, Text, useMatches } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import type { PortfolioInfo } from '~/api/queries/types';
import Currency from '~/components/Currency';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { format } from '~/lib/format';

const COLORS = [
  { id: 0, color: '#0066ff' },
  { id: 1, color: '#e85dff' },
  { id: 2, color: '#ffb800' }
];

export function BalanceCard() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = useQuery(queries.portfolio.fetchPortfolio({ portfolioId: Number(portfolioId) }));

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

  return <Inner data={data} />;
}

function BalanceContainer({ children, ...props }: CardProps) {
  return (
    <Card withBorder {...props}>
      {children}
    </Card>
  );
}

function Inner({ data }: { data: PortfolioInfo }) {
  const chartSize = useMatches({
    base: 200,
    sm: 230
  });
  const [activeSegment, setActiveSegment] = useState<{
    name: string;
    value: number;
    percent: number;
  } | null>(null);

  const sortedStocks = [...data.stocks].sort((a, b) => b.value - a.value);

  const filteredStocks = sortedStocks.filter((stock) => stock.value / data.totalValue >= 0.01);

  // Select up to the top 3 stocks
  const topStocks = filteredStocks.slice(0, 3);

  // Calculate "Other" category
  const otherStocks = sortedStocks.slice(topStocks.length); // Remaining stocks after top 3
  const otherValue = otherStocks.reduce((sum, stock) => sum + stock.value, 0);

  const pieData = [
    ...topStocks.map((stock, idx) => ({
      name: stock.symbol,
      value: stock.value,
      // biome-ignore lint/suspicious/noNonNullAssertedOptionalChain: intented
      color: COLORS.find((s) => s.id === idx)?.color!
    }))
  ];

  if (otherValue > 0) {
    pieData.push({
      name: 'Other',
      value: otherValue,
      color: 'lightgray'
    });
  }

  const handleMouseEnter = (segment: { name: string; value: number; percent: number }) => {
    setActiveSegment(segment);
  };

  const handleMouseLeave = () => {
    setActiveSegment(null);
  };
  return (
    <BalanceContainer miw={275}>
      <Group justify="center">
        <Box pos="relative">
          <DonutChart
            data={pieData}
            size={chartSize}
            thickness={18}
            paddingAngle={3}
            pieProps={{
              cornerRadius: 5,
              onMouseEnter: (segment) => handleMouseEnter(segment),
              onMouseLeave: handleMouseLeave,
              minAngle: 10
            }}
            withTooltip={false}
          />
          <Stack
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%, -50%)',
              textAlign: 'center'
            }}
            gap={4}>
            {!activeSegment ? (
              <>
                <Currency fz={rem(22)} fw={500}>
                  {data.totalValue}
                </Currency>
                {data.stocks.length !== 0 && (
                  <Text fz="sm" fw={700} c={data.totalProfitPercentage >= 0 ? 'teal' : 'red'}>
                    {`${data.totalProfit > 0 ? '+' : ''}${format.toCurrency(data.totalProfit)}`}
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
      {topStocks.length > 0 && (
        <Stack justify="center" gap="sm" mt="xl">
          <Text c="dimmed" ta="center" size="sm" fw={700}>
            Top Holdings
          </Text>
          {topStocks.map((stock, idx) => (
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
                  <Group>
                    <Text span size="xs" c={stock.profit === 0 ? 'dimmed' : stock.profit >= 0 ? 'teal' : 'red'}>
                      {format.toLocalePercentage(stock.profitPercentage)}
                    </Text>
                  </Group>
                </Group>
              </Box>
              {idx !== topStocks.length - 1 && <Divider />}
            </Stack>
          ))}
        </Stack>
      )}
    </BalanceContainer>
  );
}
