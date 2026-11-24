import { Trans, useLingui } from '@lingui/react/macro';
import { Button, VisuallyHidden } from '@mantine/core';
import { IconArrowBackUp } from '@tabler/icons-react';
import { useUndoTradeModalStore } from '~/features/trades/workflows/undo-trade-modal';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useFormat } from '~/shared/format/use-format';
import styles from './trades-list.module.css';
import type { TradeRow, UndoTrade } from './use-trades-list';

export function TradeBadge({ type }: { type: TradeRow['type'] }) {
  return (
    <span className={styles.tradeBadge} data-type={type}>
      {type === 'BUY' ? (
        <Trans comment="Short label on a purchase transaction badge">BUY</Trans>
      ) : (
        <Trans comment="Short label on a sale transaction badge">SELL</Trans>
      )}
    </span>
  );
}

export function TradeDetails({
  trade,
  history,
  opened,
  onClose
}: {
  trade?: TradeRow;
  history?: UndoTrade;
  opened: boolean;
  onClose: () => void;
}) {
  const { t } = useLingui();

  return (
    <ResponsiveOverlay
      opened={opened && !!trade}
      onClose={onClose}
      title={
        trade ? (
          <span className={styles.detailTitle}>
            <VisuallyHidden>
              <Trans>Trade details</Trans>{' '}
            </VisuallyHidden>
            <strong>{trade.position.instrumentSymbol}</strong> <TradeBadge type={trade.type} />
          </span>
        ) : (
          t`Trade details`
        )
      }
      desktop="drawer"
      size="24rem"
      drawerClassNames={{
        content: styles.detailPanel,
        header: styles.detailHeader,
        close: styles.detailClose,
        body: styles.detailBody,
        overlay: styles.detailOverlay
      }}>
      {trade && <TradeMetrics trade={trade} history={history} onClose={onClose} />}
    </ResponsiveOverlay>
  );
}

function TradeMetrics({ trade, history, onClose }: { trade: TradeRow; history?: UndoTrade; onClose: () => void }) {
  const { t } = useLingui();
  const format = useFormat();
  const openUndo = useUndoTradeModalStore((state) => state.open);
  const { type, actionDate, price, quantity: units, profit, position } = trade;
  const { currencyCode, portfolio } = position;
  const money = (amount: number) => format.toCurrency(amount, false, currencyCode);
  const quantity = format.number(units, { maximumFractionDigits: 20 });
  const rows = [
    { label: t`Date`, value: <time dateTime={actionDate}>{format.toFullDateTime(new Date(actionDate))}</time> },
    { label: t`Quantity`, value: t`${quantity} units` },
    { label: t`Unit price`, value: money(price) },
    { label: t`Total amount`, value: <strong>{money(history?.total ?? units * price)}</strong> }
  ];
  const realized = type === 'SELL' ? (profit ?? history?.profit) : undefined;
  const returnRate = type === 'SELL' ? history?.returnPercentage : undefined;
  const percentage = returnRate == null ? undefined : format.toLocalePercentage(returnRate, 2, { signDisplay: 'exceptZero' });
  const signedProfit = realized == null ? undefined : format.currency(realized, { currency: currencyCode, signDisplay: 'exceptZero' });
  const direction = realized ?? returnRate ?? 0;

  return (
    <div className={styles.details}>
      <dl>
        {rows.map(({ label, value }) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        {(signedProfit != null || percentage != null) && (
          <div>
            <dt>
              <Trans>Realized P&L</Trans>
            </dt>
            <dd className={styles.profit} data-tone={direction > 0 ? 'profit' : direction < 0 ? 'loss' : undefined}>
              {signedProfit == null ? percentage : `${signedProfit}${percentage ? ` (${percentage})` : ''}`}
            </dd>
          </div>
        )}
      </dl>
      {history?.latest && (
        <Button
          variant="default"
          className={styles.detailUndo}
          leftSection={<IconArrowBackUp size={15} />}
          onClick={() => {
            onClose();
            openUndo(history, portfolio.id, currencyCode);
          }}>
          <Trans>Undo this trade</Trans>
        </Button>
      )}
    </div>
  );
}
