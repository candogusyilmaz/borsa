import { Card } from '@mantine/core';
import type { Balance } from '~/api/queries/types';
import { BalanceChart } from './BalanceChart';
import { BalanceHoldings } from './BalanceHoldings';

export function BalanceCard({ data }: { data: Balance }) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder>
      <BalanceChart data={data} />
      <BalanceHoldings data={data} />
    </Card>
  );
}
