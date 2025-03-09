import { Alert, Button, Card, Checkbox, Group, Stack, Text } from '@mantine/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { mutations } from '~/api';
import { queryKeys } from '~/api/queries/config';
import { alerts } from '~/lib/alert';
import { format } from '~/lib/format';
import { useUndoTradeModalStore } from './UndoTradeModal';

function DetailLine({
  label,
  value,
  color
}: {
  label: string;
  value: React.ReactNode;
  color?: string;
}) {
  return (
    <Group justify="space-between" align="center">
      <Text c="dimmed" fw={400} fz="sm">
        {label}
      </Text>
      <Text fw={500} fz="sm" c={color}>
        {value}
      </Text>
    </Group>
  );
}

export function UndoTradeForm() {
  const client = useQueryClient();
  const { trade, close } = useUndoTradeModalStore();
  const [hasConfirmed, setHasConfirmed] = useState(false);

  const mutation = useMutation({
    ...mutations.trades.undo,
    onSuccess: () => {
      close();
      alerts.success(`Trade succesfully undone for symbol ${trade.symbol}.`);
      client.invalidateQueries({
        predicate: (q) => q.queryKey.includes(queryKeys.portfolio)
      });
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const handleFormSubmit = () => {
    mutation.mutate(trade.holdingId);
  };

  return (
    <Stack>
      <Text size="lg" fw={500}>
        Undo trade for{' '}
        <Text span fw={700} c="blue">
          {trade.symbol}
        </Text>
      </Text>

      {/* Add trade details section */}
      <Card p="sm" withBorder>
        <Stack gap="xs">
          <Text size="sm" fw={500}>
            Trade Summary
          </Text>
          <DetailLine label="Date" value={format.toFullDateTime(new Date(trade.date))} />
          <DetailLine label="Quantity" value={trade.quantity} />
          <DetailLine label="Price" value={format.toCurrency(trade.price, false)} />
          <DetailLine label="Total" value={format.toCurrency(trade.total, false)} />
        </Stack>
      </Card>

      <Alert title="Warning" color="red" variant="filled">
        This will permanently remove this trade record. This action cannot be reversed. Daily portfolio snapshots will not be retroactively
        modified.
      </Alert>

      <Group>
        <Checkbox label="I understand this action is permanent" onChange={(e) => setHasConfirmed(e.currentTarget.checked)} />
      </Group>

      <Group justify="flex-end">
        <Button variant="subtle" color="gray" onClick={close} disabled={mutation.isPending}>
          Cancel
        </Button>

        <Button onClick={handleFormSubmit} color="red" loading={mutation.isPending} disabled={!hasConfirmed || mutation.isSuccess}>
          Confirm Removal
        </Button>
      </Group>
    </Stack>
  );
}
