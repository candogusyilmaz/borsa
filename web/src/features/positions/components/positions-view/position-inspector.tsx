import { Button, Group } from '@mantine/core';
import { IconMinus, IconPlus } from '@tabler/icons-react';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics, isDataStale } from '~/features/positions/position-presentation';
import { TradesList } from '~/features/trades/components/trades-list/trades-list';
import { useTransactionModalStore } from '~/features/trades/workflows/transaction-modal';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { format } from '~/shared/format/format';
import styles from './positions-view.module.css';

export function PositionInspector({ position, onClose }: { position?: Position; onClose: () => void }) {
  const openTrade = useTransactionModalStore((state) => state.open);

  function startTrade(type: 'Buy' | 'Sell') {
    if (!position) return;
    onClose();
    openTrade(type, position.instrument.id, position.portfolio.id);
  }

  return (
    <ResponsiveOverlay
      opened={!!position}
      onClose={onClose}
      title={position?.instrument.symbol ?? 'Position'}
      desktop="drawer"
      size="32rem">
      {position && (
        <div className={styles.inspector}>
          <p className={styles.muted}>
            {position.instrument.name} · {position.portfolio.name}
          </p>
          <PositionMetrics position={position} />
          <Group gap="xs">
            <Button leftSection={<IconPlus size={16} />} onClick={() => startTrade('Buy')}>
              Buy
            </Button>
            <Button leftSection={<IconMinus size={16} />} variant="default" onClick={() => startTrade('Sell')}>
              Sell
            </Button>
          </Group>
          <section aria-label="Position trade history">
            <h3>Position trade history</h3>
            <TradesList positionId={position.id} />
          </section>
        </div>
      )}
    </ResponsiveOverlay>
  );
}

function PositionMetrics({ position }: { position: Position }) {
  const { price, value, profit, returnPercentage, dailyProfit } = getPositionMetrics(position);
  const money = (amount: number) => format.toCurrency(amount, false, position.currencyCode);
  const metrics = [
    { label: 'Quantity', value: position.quantity.toLocaleString('tr-TR', { maximumFractionDigits: 20 }) },
    { label: 'Average cost', value: money(position.avgCost) },
    { label: 'Cost basis', value: money(position.total) },
    { label: 'Price', value: price == null ? undefined : money(price) },
    { label: 'Total value', value: value == null ? undefined : money(value) },
    { label: 'Unrealized P/L', value: profit == null ? undefined : money(profit) },
    { label: 'Return', value: returnPercentage == null ? undefined : format.toLocalePercentage(returnPercentage) },
    { label: 'Daily change', value: dailyProfit == null ? undefined : money(dailyProfit) }
  ];

  return (
    <>
      <dl className={styles.metrics}>
        {metrics.map(
          ({ label, value }) =>
            value != null && (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            )
        )}
      </dl>
      {isDataStale(position.instrument.updatedAt) && position.instrument.updatedAt && (
        <p className={styles.muted}>Price as of {format.toShortDateTime(new Date(position.instrument.updatedAt))}</p>
      )}
    </>
  );
}
