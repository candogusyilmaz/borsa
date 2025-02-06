import { Card, Group, SimpleGrid, Space, Text, Title } from "@mantine/core";
import type { Balance } from "~/api/queries/types";
import { format } from "~/lib/format";
import { BuyStockModal } from "../Stocks/BuyStockModal";
import { SellStockModal } from "../Stocks/SellStockModal";

export function HoldingsCard({ data }: { data: Balance }) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder flex={1}>
      <Title order={3}>Holdings</Title>
      <Space my="md" />
      <SimpleGrid cols={{ xs: 1, md: 2 }}>
        {data.stocks.map((s) => (
          <Card withBorder shadow="0" key={`hhh-${s.symbol}`}>
            <Group justify="space-between" align="center">
              <Text fw={500}>{s.symbol}</Text>
              <Text fw={600} size="sm">
                {format.toCurrency(s.currentPrice * s.quantity)}
              </Text>
            </Group>
            <Group justify="space-between" align="center">
              <Text c="dimmed" size="sm">
                {s.quantity} shares
              </Text>
              <Text
                size="xs"
                c={
                  s.profitPercentage === 0
                    ? "dimmed"
                    : s.profitPercentage > 0
                      ? "teal"
                      : "red"
                }
              >
                {format.toCurrency(s.profit)}
              </Text>
            </Group>
            <Group mt="sm" justify="space-between" align="center" gap="lg">
              <BuyStockModal
                symbol={s.symbol}
                price={s.currentPrice}
                stockId={s.id}
                buttonProps={{
                  flex: 1,
                  variant: "filled",
                }}
              />
              <SellStockModal
                symbol={s.symbol}
                price={s.currentPrice}
                stockId={s.id}
                buttonProps={{
                  flex: 1,
                  variant: "filled",
                }}
              />
            </Group>
          </Card>
        ))}
      </SimpleGrid>
    </Card>
  );
}
