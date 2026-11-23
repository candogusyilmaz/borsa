import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Group } from '@mantine/core';
import { IconMinus, IconPlus } from '@tabler/icons-react';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics, isDataStale } from '~/features/positions/position-presentation';
import { TradesList } from '~/features/trades/components/trades-list/trades-list';
import { useTransactionModalStore } from '~/features/trades/workflows/transaction-modal';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useFormat } from '~/shared/format/use-format';
import styles from './positions-view.module.css';

export function PositionInspector({ position, onClose }: { position?: Position; onClose: () => void }) {
  const { t } = useLingui();
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
      title={position?.instrument.symbol ?? t({ comment: 'Fallback title for the details of a holding', message: 'Position' })}
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
              <Trans comment="Button or filter for a purchase of an asset">Buy</Trans>
            </Button>
            <Button leftSection={<IconMinus size={16} />} variant="default" onClick={() => startTrade('Sell')}>
              <Trans comment="Button or filter for a sale of an asset">Sell</Trans>
            </Button>
          </Group>
          <section aria-label={t`Position trade history`}>
            <h3>
              <Trans>Position trade history</Trans>
            </h3>
            <TradesList positionId={position.id} />
          </section>
        </div>
      )}
    </ResponsiveOverlay>
  );
}

function PositionMetrics({ position }: { position: Position }) {
  const { t } = useLingui();
  const format = useFormat();
  const { price, value, profit, returnPercentage, dailyProfit } = getPositionMetrics(position);
  const money = (amount: number) => format.toCurrency(amount, false, position.currencyCode);
  const metrics = [
    { label: t`Quantity`, value: format.number(position.quantity, { maximumFractionDigits: 20 }) },
    { label: t`Average cost`, value: money(position.avgCost) },
    { label: t`Cost basis`, value: money(position.total) },
    { label: t({ comment: 'Unit price of a financial instrument', message: 'Price' }), value: price == null ? undefined : money(price) },
    { label: t`Total value`, value: value == null ? undefined : money(value) },
    { label: t`Unrealized P/L`, value: profit == null ? undefined : money(profit) },
    {
      label: t({ comment: 'Percentage investment gain or loss, not a navigation action', message: 'Return' }),
      value: returnPercentage == null ? undefined : format.toLocalePercentage(returnPercentage)
    },
    { label: t`Daily change`, value: dailyProfit == null ? undefined : money(dailyProfit) }
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
        <p className={styles.muted}>
          <Trans>Price as of {format.toShortDateTime(new Date(position.instrument.updatedAt))}</Trans>
        </p>
      )}
    </>
  );
}
