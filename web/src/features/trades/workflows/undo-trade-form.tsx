import { Trans, useLingui } from '@lingui/react/macro';
import { Alert, Button, Checkbox, Group, Stack, Text } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { alerts } from '~/shared/feedback/alerts';
import { useFormat } from '~/shared/format/use-format';
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
  const { t } = useLingui();
  const format = useFormat();
  const { portfolioId: routePortfolioId } = useParams({ strict: false });
  const client = useQueryClient();
  const { trade, close, portfolioId: selectedPortfolioId, currencyCode } = useUndoTradeModalStore();
  const portfolioId = selectedPortfolioId ?? routePortfolioId;
  const [hasConfirmed, setHasConfirmed] = useState(false);

  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/undo/{holdingId}', {
    onSuccess: () => {
      close();
      const { symbol } = trade;
      alerts.success(t`Trade undone for ${symbol}.`);
      client.invalidateQueries();
    },
    onError: () => alerts.error(t`Could not undo the trade. Try again.`)
  });

  const handleFormSubmit = () => {
    if (!portfolioId) {
      alerts.error(t`Portfolio ID is required to perform this action.`);
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
        <Trans>
          Undo trade for <strong>{trade.symbol}</strong>
        </Trans>
      </Text>

      <div>
        <Stack gap="xs">
          <Text size="sm" fw={500}>
            <Trans>Trade Summary</Trans>
          </Text>
          <DetailLine label={t`Date`} value={format.toFullDateTime(new Date(trade.date))} />
          <DetailLine label={t`Quantity`} value={format.number(trade.quantity)} />
          <DetailLine
            label={t({ comment: 'Unit price of a financial instrument', message: 'Price' })}
            value={format.toCurrency(trade.price, false, currencyCode)}
          />
          <DetailLine label={t`Total`} value={format.toCurrency(trade.total, false, currencyCode)} />
        </Stack>
      </div>

      <Alert
        title={t({ comment: 'Heading before explaining the permanent removal of a recorded trade', message: 'Warning' })}
        color="loss"
        variant="light">
        <Trans>
          This will permanently remove this trade record. This action cannot be reversed. Daily portfolio snapshots will not be
          retroactively modified.
        </Trans>
      </Alert>

      <Group>
        <Checkbox label={t`I understand this action is permanent`} onChange={(e) => setHasConfirmed(e.currentTarget.checked)} />
      </Group>

      <Group justify="flex-end">
        <Button variant="subtle" color="gray" onClick={close} disabled={mutation.isPending}>
          <Trans>Cancel</Trans>
        </Button>

        <Button onClick={handleFormSubmit} color="loss" loading={mutation.isPending} disabled={!hasConfirmed || mutation.isSuccess}>
          <Trans>Confirm Removal</Trans>
        </Button>
      </Group>
    </Stack>
  );
}
