import { DonutChart } from '@mantine/charts';
import { Box, Group, Stack, Text, rem } from '@mantine/core';
import { useState } from 'react';
import type { Balance } from '~/api/queries/types';
import Currency from '~/components/Currency';
import { format } from '~/lib/format';

const COLORS = [
  { id: 0, color: '#0066ff' },
  { id: 1, color: '#e85dff' },
  { id: 2, color: '#ffb800' }
];

export function BalanceChart({ data }: { data: Balance }) {
  const sortedStocks = [...data.stocks].sort((a, b) => b.value - a.value);
  const topStocks = sortedStocks.slice(0, 3);
  const otherStocks = sortedStocks.slice(3);
  const otherValue = otherStocks.reduce((sum, stock) => sum + stock.value, 0);

  const pieData = [
    ...topStocks.map((stock, idx) => ({
      name: stock.symbol,
      value: stock.value,
      color: COLORS.find((s) => s.id === idx)?.color!
    })),
    {
      name: 'Other',
      value: otherValue,
      color: 'lightgray'
    }
  ];

  const [activeSegment, setActiveSegment] = useState<{
    name: string;
    value: number;
    percent: number;
  } | null>(null);

  const handleMouseEnter = (segment: {
    name: string;
    value: number;
    percent: number;
  }) => {
    setActiveSegment(segment);
  };

  const handleMouseLeave = () => {
    setActiveSegment(null);
  };
  return (
    <Group justify="center">
      <Box pos="relative">
        <DonutChart
          data={pieData}
          size={250}
          thickness={15}
          paddingAngle={3}
          pieProps={{
            cornerRadius: 5,
            onMouseEnter: (segment) => handleMouseEnter(segment),
            onMouseLeave: handleMouseLeave
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
          gap={16}>
          {!activeSegment ? (
            <>
              <Text size={rem(18)} fw={700}>
                Balance
              </Text>
              <Currency size={rem(22)} fw={500} c="teal">
                {data.totalValue}
              </Currency>
              <Text size="xs" fw={700} c={data.totalProfitPercentage >= 0 ? 'teal' : 'red'}>
                {format.toLocalePercentage(data.totalProfitPercentage)}
              </Text>
            </>
          ) : (
            <>
              <Text size={rem(22)} fw={700}>
                {activeSegment.name}
              </Text>
              <Text size={rem(18)} fw={500} c="teal">
                {format.toLocalePercentage(activeSegment.percent)}
              </Text>
            </>
          )}
        </Stack>
      </Box>
    </Group>
  );
}
