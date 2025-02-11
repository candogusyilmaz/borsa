import { Box, Divider, Group, Stack, Text } from '@mantine/core';
import { useSuspenseQuery } from '@tanstack/react-query';
import { queries } from '~/api';
import Currency from '~/components/Currency';
import { format } from '~/lib/format';

export function BalanceHoldings() {
  const {
    data: { stocks }
  } = useSuspenseQuery(queries.member.balance());
  const sortedStocks = stocks?.sort((a, b) => b.value - a.value);
  const topStocks = sortedStocks.slice(0, 3);

  if (stocks.length === 0) {
    return <></>;
  }

  return (
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
                {stock.quantity} shares @ <Currency span>{stock.averagePrice}</Currency>
              </Text>
              <Group>
                <Text span size="xs" c={stock.profit === 0 ? 'dimmed' : stock.profit >= 0 ? 'teal' : 'red'}>
                  {format.toLocalePercentage(stock.profitPercentage)}
                </Text>
              </Group>
            </Group>
          </Box>
          {idx !== topStocks.length - 1 && <Divider color="gray" />}
        </Stack>
      ))}
    </Stack>
  );
}
