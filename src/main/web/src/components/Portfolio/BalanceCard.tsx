import { Card } from "@mantine/core";
import { BalanceChart } from "./BalanceChart";
import { BalanceHoldings } from "./BalanceHoldings";

export function BalanceCard() {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder>
      <BalanceChart />
      <BalanceHoldings />
    </Card>
  );
}
