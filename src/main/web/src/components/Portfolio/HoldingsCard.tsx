import {
  Card,
  Center,
  Group,
  SimpleGrid,
  Space,
  Text,
  Title,
} from "@mantine/core";
import { format } from "~/lib/format";
import { BuyStockModal } from "../Stocks/BuyStockModal";
import { SellStockModal } from "../Stocks/SellStockModal";
import { useSuspenseQuery } from "@tanstack/react-query";
import { queries } from "~/api";

export function HoldingsCard() {
  const { data } = useSuspenseQuery(queries.member.balance());

  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder flex={1}>
      <Title order={3}>Holdings</Title>
      {data.stocks.length > 0 && <Space my="md" />}
      {data.stocks.length > 0 && (
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
      )}
      {data.stocks.length === 0 && (
        <Center h="100%" mih={100}>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            You currently don't own any holdings
          </Text>
        </Center>
      )}
    </Card>
  );
}
