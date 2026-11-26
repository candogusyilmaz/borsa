import { Trans, useLingui } from '@lingui/react/macro';
import { Button } from '@mantine/core';
import { IconAlertCircle, IconMinus, IconPlus, IconX } from '@tabler/icons-react';
import { useId } from 'react';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionCurrencyOptions, getPositionMetrics, isDataStale } from '~/features/positions/position-presentation';
import { TradesList } from '~/features/trades/components/trades-list/trades-list';
import { useTransactionModalStore } from '~/features/trades/workflows/transaction-modal';
import { FinanceBadge } from '~/shared/components/finance-badge/finance-badge';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { getFinanceTone } from '~/shared/format/finance-tone';
import { useFormat } from '~/shared/format/use-format';
import styles from './position-inspector.module.css';

interface InspectorProps {
  position: Position;
  onClose: () => void;
  weight?: number;
  market?: boolean;
}

export function PositionInspector({ position, onClose, weight, market }: Omit<InspectorProps, 'position'> & { position?: Position }) {
  const { t } = useLingui();

  return (
    <ResponsiveOverlay
      opened={!!position}
      onClose={onClose}
      title={
        position ? (
          <InspectorIdentity position={position} market={market} />
        ) : (
          t({ comment: 'Fallback title for the details of a holding', message: 'Position' })
        )
      }
      desktop="drawer"
      size={380}
      drawerClassNames={{
        content: styles.panel,
        header: styles.header,
        title: styles.title,
        close: styles.close,
        body: styles.body,
        overlay: styles.overlay
      }}>
      {position && <InspectorBody position={position} weight={weight} market={market} onClose={onClose} />}
    </ResponsiveOverlay>
  );
}

export function InlinePositionInspector(props: InspectorProps) {
  const { t } = useLingui();
  const titleId = useId();

  return (
    <aside className={`${styles.panel} ${styles.inline}`} aria-labelledby={titleId}>
      <header className={styles.header}>
        <h2 id={titleId} className={styles.title}>
          <InspectorIdentity position={props.position} market />
        </h2>
        <button type="button" className={styles.inlineClose} aria-label={t`Close position details`} onClick={props.onClose}>
          <IconX size={16} aria-hidden="true" />
        </button>
      </header>
      <InspectorBody {...props} />
    </aside>
  );
}

function InspectorIdentity({ position, market }: { position: Position; market?: boolean }) {
  return (
    <span className={styles.identity}>
      <TickerAvatar symbol={position.instrument.symbol} size="md" />
      <span className={styles.instrument}>
        <span className={styles.titleLine}>
          <strong>{position.instrument.symbol}</strong>
          <span className={styles.portfolio} data-market={market || undefined}>
            {position.portfolio.name}
          </span>
        </span>
        <span className={styles.instrumentName}>{position.instrument.name}</span>
      </span>
    </span>
  );
}

function InspectorBody({ position, onClose, weight, market }: InspectorProps) {
  const openTrade = useTransactionModalStore((state) => state.open);

  function startTrade(type: 'Buy' | 'Sell') {
    onClose();
    openTrade(type, position.instrument.id, position.portfolio.id);
  }

  return (
    <>
      <div className={styles.scrollArea}>
        <PositionMetrics position={position} weight={weight} market={market} />
        <TradesList positionId={position.id} onUndo={onClose} />
      </div>
      <footer className={styles.actions}>
        <Button className={styles.buy} leftSection={<IconPlus size={14} />} onClick={() => startTrade('Buy')}>
          {market ? <Trans>Add purchase</Trans> : <Trans comment="Buy action in the footer of the position drawer">Purchase</Trans>}
        </Button>
        <Button className={styles.sell} leftSection={<IconMinus size={14} />} variant="default" onClick={() => startTrade('Sell')}>
          {market ? <Trans>Add sale</Trans> : <Trans comment="Sell action in the footer of the position drawer">Sale</Trans>}
        </Button>
      </footer>
    </>
  );
}

function PositionMetrics({ position, weight, market }: { position: Position; weight?: number; market?: boolean }) {
  const { t } = useLingui();
  const format = useFormat();
  const { price, value, profit, returnPercentage, dailyProfit, dailyChangePercentage } = getPositionMetrics(position);
  const currency = getPositionCurrencyOptions(position.currencyCode, market);
  const money = (amount: number) => format.currency(amount, { ...currency, minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const signedMoney = (amount: number) => format.currency(amount, { ...currency, signDisplay: 'exceptZero' });
  const percent = (amount: number) => format.toLocalePercentage(amount, 2, { signDisplay: 'exceptZero' });
  const tone = getFinanceTone(profit);
  const dailyChange =
    dailyChangePercentage == null ? (dailyProfit == null ? undefined : signedMoney(dailyProfit)) : percent(dailyChangePercentage);
  const quantity = format.number(position.quantity, { maximumFractionDigits: 20 });
  const metrics = [
    { label: t`Quantity`, value: t({ comment: 'Number of units held in the position', message: `${quantity} units` }) },
    { label: t`Average cost`, value: money(position.avgCost) },
    { label: t`Current price`, value: price == null ? (market ? t`No price` : undefined) : money(price) },
    {
      label: t`Daily change`,
      value:
        !market && dailyChange != null ? (
          <FinanceBadge variant="text" tone={getFinanceTone(dailyChangePercentage ?? dailyProfit)}>
            {dailyChange}
          </FinanceBadge>
        ) : undefined
    },
    { label: t`Cost basis`, value: money(position.total) },
    {
      label: market
        ? t({ comment: 'Position share of the market value shown in the filtered positions list', message: 'Weight in view' })
        : t`Portfolio weight`,
      value: weight == null ? (market ? '—' : undefined) : format.toLocalePercentage(weight)
    }
  ];

  return (
    <>
      <dl className={styles.hero}>
        <div>
          <dt>
            <Trans>Position value</Trans>
          </dt>
          <dd className={styles.value}>{value != null ? money(value) : market ? <Trans>Awaiting price</Trans> : '—'}</dd>
        </div>
        {market && value == null && (
          <p className={styles.missingPrice}>
            <IconAlertCircle size={14} aria-hidden="true" />
            <Trans>Market data is unavailable for this symbol.</Trans>
          </p>
        )}
        {profit != null && (
          <div>
            <dt className={styles.visuallyHidden}>
              <Trans>Unrealized P/L</Trans>
            </dt>
            <dd className={styles.return}>
              <FinanceBadge variant="text" tone={tone}>
                {signedMoney(profit)}
              </FinanceBadge>
              {returnPercentage != null && (
                <FinanceBadge className={styles.returnBadge} tone={tone}>
                  {percent(returnPercentage)}
                </FinanceBadge>
              )}
            </dd>
          </div>
        )}
      </dl>
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
        <p className={styles.stale}>
          <Trans>Price as of {format.toShortDateTime(new Date(position.instrument.updatedAt))}</Trans>
        </p>
      )}
    </>
  );
}
