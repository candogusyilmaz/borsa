import { Card, Group, Skeleton, Stack, Text } from '@mantine/core';
import { format } from '~/lib/format';

type BuyTransactionSummaryProps = {
  stockInHolding?: {
    quantity: number;
    averagePrice: number;
    cost: number;
  } | null;
  newQuantity: number | null;
  newPrice: number | null;
  isValid: boolean;
};

function SummaryLine({
  label,
  value
}: {
  label: string;
  value: React.ReactNode;
}) {
  return (
    <Group justify="space-between" align="center">
      <Text c="dimmed" fw={400} fz="xs">
        {label}
      </Text>
      <Text fw={500} fz="xs">
        {value}
      </Text>
    </Group>
  );
}

export function BuyTransactionSummary({ stockInHolding, newQuantity, newPrice, isValid }: BuyTransactionSummaryProps) {
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

  const calculateAveragePrice = () => {
    if (!newQuantity || !newPrice) return 0;
    if (!stockInHolding) return newPrice;

    return (stockInHolding.cost + newPrice * newQuantity) / (stockInHolding.quantity + newQuantity);
  };

  const calculateTotal = () => {
    if (!newQuantity || !newPrice) return 0;
    if (!stockInHolding) return newPrice * newQuantity;
    return stockInHolding.cost + newPrice * newQuantity;
  };

  const calculateCost = () => {
    if (!newQuantity || !newPrice) return 0;
    return newPrice * newQuantity;
  };

  return (
    <Card p="sm" withBorder>
      <Stack gap="sm">
        <Text fw={500} fz="xs">
          Summary
        </Text>
        <SummaryLine
          label="Quantity"
          value={`${stockInHolding?.quantity || 0} > ${stockInHolding ? stockInHolding.quantity + (newQuantity ?? 0) : (newQuantity ?? 0)}`}
        />
        <SummaryLine
          label="Average price"
          value={`${format.toCurrency(stockInHolding?.averagePrice || 0, false)} > ${format.toCurrency(calculateAveragePrice(), false)}`}
        />
        <SummaryLine label="Cost" value={format.toCurrency(calculateCost(), false)} />
        <SummaryLine
          label="Total"
          value={`${format.toCurrency(stockInHolding ? stockInHolding.cost : 0, false)} > ${format.toCurrency(calculateTotal(), false)}`}
        />
      </Stack>
    </Card>
  );
}
