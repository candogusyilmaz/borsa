import { Card, Group, Skeleton, Stack, Text } from '@mantine/core';
import type { PortfolioStock } from '~/api/queries/types';
import { format } from '~/lib/format';

type SellTransactionSummaryProps = {
  stockInHolding?: PortfolioStock;
  newQuantity: number | null;
  newPrice: number | null;
  isValid: boolean;
};

function SummaryLine({ label, value, color }: { label: string; value: React.ReactNode; color?: string }) {
  return (
    <Group justify="space-between" align="center">
      <Text c="dimmed" fw={400} fz="xs">
        {label}
      </Text>
      <Text fw={500} fz="xs" c={color}>
        {value}
      </Text>
    </Group>
  );
}

export function SellTransactionSummary({ stockInHolding, newQuantity, newPrice, isValid }: SellTransactionSummaryProps) {
  if (!isValid) {
    return (
      <Card p="sm" withBorder>
        <Stack gap="sm">
          <Text fw={500} fz="xs">
            Summary
          </Text>
          <Skeleton height={18} radius="sm" />
          <Skeleton height={18} radius="sm" />
          <Skeleton height={18} radius="sm" />
        </Stack>
      </Card>
    );
  }

  const calculateProfit = () => {
    if (!newQuantity || !newPrice || !stockInHolding) return 0;

    return newPrice * newQuantity - stockInHolding.averagePrice * newQuantity;
  };

  const calculateValue = () => {
    if (!newQuantity || !stockInHolding) return 0;

    return stockInHolding?.averagePrice * (stockInHolding?.quantity - newQuantity);
  };

  return (
    <Card p="sm" withBorder>
      <Stack gap="sm">
        <Text fw={500} fz="xs">
          Summary
        </Text>
        <SummaryLine
          label="Quantity"
          value={`${stockInHolding?.quantity || 0} > ${stockInHolding ? stockInHolding.quantity - (newQuantity ?? 0) : (newQuantity ?? 0)}`}
        />
        <SummaryLine
          label="Profit"
          value={`${format.toCurrency(calculateProfit(), false)}`}
          color={calculateProfit() >= 0 ? 'teal' : 'red'}
        />
        <SummaryLine label="Remaining Total" value={`${format.toCurrency(calculateValue(), false)}`} />
      </Stack>
    </Card>
  );
}
