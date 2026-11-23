import { Alert, Button, Checkbox, Group, Stack, Text } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { alerts } from '~/shared/feedback/alerts';
import { format } from '~/shared/format/format';
import { useUndoTradeModalStore } from './undo-trade-modal';

function DetailLine({ label, value, color }: { label: string; value: React.ReactNode; color?: string }) {
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
  const { portfolioId: routePortfolioId } = useParams({ strict: false });
  const client = useQueryClient();
  const { trade, close, portfolioId: selectedPortfolioId, currencyCode } = useUndoTradeModalStore();
  const portfolioId = selectedPortfolioId ?? routePortfolioId;
  const [hasConfirmed, setHasConfirmed] = useState(false);

  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/undo/{holdingId}', {
    onSuccess: () => {
      close();
      alerts.success(`Trade succesfully undone for symbol ${trade.symbol}.`);
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const handleFormSubmit = () => {
    if (!portfolioId) {
      alerts.error('Portfolio ID is required to perform this action.');
      return;
    }

    mutation.mutate({
      params: {
        path: {
          portfolioId: Number(portfolioId),
          holdingId: Number(trade.holdingId)
        }
      }
    });
  };

  return (
    <Stack>
      <Text size="lg" fw={500}>
        Undo trade for{' '}
        <Text span fw={600} c="var(--mantine-color-text)">
          {trade.symbol}
        </Text>
      </Text>

      <div>
        <Stack gap="xs">
          <Text size="sm" fw={500}>
            Trade Summary
          </Text>
          <DetailLine label="Date" value={format.toFullDateTime(new Date(trade.date))} />
          <DetailLine label="Quantity" value={trade.quantity} />
          <DetailLine label="Price" value={format.toCurrency(trade.price, false, currencyCode)} />
          <DetailLine label="Total" value={format.toCurrency(trade.total, false, currencyCode)} />
        </Stack>
      </div>

      <Alert title="Warning" color="loss" variant="light">
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

        <Button onClick={handleFormSubmit} color="loss" loading={mutation.isPending} disabled={!hasConfirmed || mutation.isSuccess}>
          Confirm Removal
        </Button>
      </Group>
    </Stack>
  );
}
