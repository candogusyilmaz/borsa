import { Card, Group, Stack, Text } from "@mantine/core";
import { useState } from "react";
import type { Balance } from "~/api/queries/types";
import Currency from "~/components/Currency";
import { format } from "~/lib/format";

export function BalanceHoldings({ data: { stocks } }: { data: Balance }) {
  const [sortedStocks] = useState(stocks.sort((a, b) => b.value - a.value));
  const topStocks = sortedStocks.slice(0, 3);

  return (
    <Stack justify="center" gap="md" mt="xl">
      <Text c="dimmed" ta="center" size="sm">
        Top Holdings
      </Text>
      {topStocks.map((stock) => (
        <Card shadow="0" key={stock.symbol} withBorder padding="sm" radius="sm">
          <Group justify="space-between">
            <Text>{stock.symbol}</Text>
            <Currency fw={500}>{stock.value}</Currency>
          </Group>
          <Group justify="space-between">
            <Text size="xs" c="dimmed">
              {stock.quantity} shares @{" "}
              <Currency span>{stock.averagePrice}</Currency>
            </Text>
            <Group>
              <Text
                span
                size="xs"
                c={
                  stock.profit === 0
                    ? "dimmed"
                    : stock.profit >= 0
                      ? "teal"
                      : "red"
                }
              >
                {format.toLocalePercentage(stock.profitPercentage)}
              </Text>
            </Group>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
