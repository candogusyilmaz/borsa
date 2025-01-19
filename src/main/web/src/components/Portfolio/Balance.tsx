import { Card } from "@mantine/core";
import { BalanceChart } from "./BalanceChart";
import { BalanceHoldings } from "./BalanceHoldings";
import type { Balance } from "~/api/queries/types";

export function BalanceCard({ data }: { data: Balance }) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder w={325}>
      <BalanceChart data={data} />
      <BalanceHoldings data={data} />
    </Card>
  );
}
