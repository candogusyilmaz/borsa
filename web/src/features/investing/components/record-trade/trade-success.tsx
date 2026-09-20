import { Button, Stack, Text } from '@mantine/core';
import { CheckCircleIcon } from '@phosphor-icons/react';
import { formatMoney } from '@/shared/format/money';
import { formatQuantity, getTradeSideLabel } from '../../utils/investing-formatters';
import classes from './record-trade.module.css';
import type { useTradeSession } from './use-trade-session';

interface TradeSuccessProps {
  session: ReturnType<typeof useTradeSession>;
  onClose: () => void;
  onViewDetails?: (activityId: string) => void;
}

export function TradeSuccess({ session, onClose, onViewDetails }: TradeSuccessProps) {
  const { state, resetSession } = session;

  if (state.step !== 'success') return null;
  const { trade } = state;

  return (
    <div className={classes.successCard}>
      <div className={classes.successIcon}>
        <CheckCircleIcon size={32} weight="bold" />
      </div>

      <Stack gap={4} align="center">
        <Text fw={700} size="lg">
          Trade Completed Successfully
        </Text>
        <Text size="sm" c="dimmed">
          {getTradeSideLabel(trade.side)} {formatQuantity(trade.quantity)} {trade.instrumentSymbol} @{' '}
          {formatMoney(trade.unitPrice, trade.currency)}
        </Text>
        <Text size="xs" c="dimmed">
          Total Cash Impact: {trade.cashDelta.startsWith('-') ? '' : '+'}
          {formatMoney(trade.cashDelta, trade.currency)}
        </Text>
      </Stack>

      <div className={classes.actions} style={{ width: '100%', marginTop: '1.5rem' }}>
        <Button variant="default" size="md" className={classes.actionBtn} onClick={resetSession}>
          Record Another Trade
        </Button>
        {onViewDetails && (
          <Button
            variant="light"
            color="brand"
            size="md"
            className={classes.actionBtn}
            onClick={() => {
              onClose();
              onViewDetails(trade.id);
            }}>
            View Details
          </Button>
        )}
        <Button variant="filled" color="brand" size="md" className={classes.actionBtn} onClick={onClose}>
          Done
        </Button>
      </div>
    </div>
  );
}
