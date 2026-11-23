import { Button, Skeleton, Tooltip } from '@mantine/core';
import type { usePositions } from '~/features/positions';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics } from '~/features/positions/position-presentation';
import { format } from '~/shared/format/format';
import styles from './portfolio-overview.module.css';

interface OverviewProps {
  query: ReturnType<typeof usePositions>;
  onOpenPosition: (position: Position) => void;
}

export function PortfolioOverview({ query, onOpenPosition }: OverviewProps) {
  if (query.isPending) return <Skeleton h={150} aria-label="Loading portfolio overview" />;
  if (query.isError) {
    return (
      <div className={styles.state}>
        <p role="alert">Could not load the portfolio balance.</p>
        <Button variant="default" onClick={() => void query.refetch()}>
          Try again
        </Button>
      </div>
    );
  }
  if (!query.data.length) return <p className={styles.state}>Record a trade to see your balance and allocation.</p>;

  const groups = new Map<string, Position[]>();
  for (const position of query.data) {
    const group = groups.get(position.currencyCode) ?? [];
    group.push(position);
    groups.set(position.currencyCode, group);
  }

  return (
    <section className={styles.hero} aria-label="Portfolio balance and allocation">
      {[...groups].map(([currency, positions]) => (
        <CurrencyBalance key={currency} currency={currency} positions={positions} onOpenPosition={onOpenPosition} />
      ))}
    </section>
  );
}

function CurrencyBalance({
  currency,
  positions,
  onOpenPosition
}: {
  currency: string;
  positions: Position[];
  onOpenPosition: OverviewProps['onOpenPosition'];
}) {
  const priced = positions
    .flatMap((position) => {
      const { value, profit } = getPositionMetrics(position);
      return value != null ? [{ position, value, profit }] : [];
    })
    .sort((a, b) => b.value - a.value);
  const total = priced.reduce((sum, { value }) => sum + value, 0);
  const profit = priced.reduce((sum, item) => sum + (item.profit ?? 0), 0);
  const costBasis = priced.reduce((sum, { position }) => sum + position.quantity * position.avgCost, 0);
  const partial = priced.length !== positions.length;
  const segments = priced.filter(({ value }) => value > 0);
  const formatMoney = (value: number) => format.toCurrency(value, false, currency);

  return (
    <div className={styles.currency}>
      <div className={styles.balance}>
        <dl>
          <dt>
            {partial ? 'Quoted balance' : 'Portfolio balance'} · {currency}
          </dt>
          <dd>{priced.length ? formatMoney(total) : '—'}</dd>
        </dl>
        {priced.length > 0 && (
          <div className={styles.return}>
            <span className="finance-badge" data-tone={profit > 0 ? 'profit' : profit < 0 ? 'loss' : undefined}>
              {format.currency(profit, { currency, signDisplay: 'exceptZero' })}
              {costBasis > 0 && ` · ${format.toLocalePercentage((profit / costBasis) * 100)}`}
            </span>
            <small>Unrealized P&L</small>
          </div>
        )}
      </div>
      {partial && (
        <p className={styles.state}>
          {positions.length - priced.length} {positions.length - priced.length === 1 ? 'position has' : 'positions have'} no current quote.
        </p>
      )}
      {total > 0 && segments.length > 0 && (
        <div className={styles.allocation}>
          <p>Asset allocation</p>
          <fieldset className={styles.allocationBar} aria-label={`${currency} allocation by quoted value`}>
            {segments.map(({ position, value }, index) => {
              const weight = format.toLocalePercentage((value / total) * 100, 1);
              const label = `${position.instrument.symbol} · ${weight} · ${formatMoney(value)}`;
              return (
                <Tooltip key={position.id} label={label} events={{ hover: true, focus: true, touch: false }}>
                  <button
                    type="button"
                    style={{ flexGrow: value / total }}
                    data-color={index % 4}
                    aria-label={label}
                    aria-haspopup="dialog"
                    onClick={() => onOpenPosition(position)}
                  />
                </Tooltip>
              );
            })}
          </fieldset>
          <ul className={styles.legend}>
            {segments.slice(0, 4).map(({ position, value }, index) => (
              <li key={position.id}>
                <button type="button" data-color={index} aria-haspopup="dialog" onClick={() => onOpenPosition(position)}>
                  <span aria-hidden="true" />
                  <strong>{position.instrument.symbol}</strong>
                  <small>{format.toLocalePercentage((value / total) * 100, 1)}</small>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
