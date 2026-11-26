import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, Skeleton, Tooltip } from '@mantine/core';
import { useState } from 'react';
import type { usePositions } from '~/features/positions';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionHighlightProps, type PositionHighlight } from '~/features/positions/position-presentation';
import { FinanceBadge } from '~/shared/components/finance-badge/finance-badge';
import { InlineState } from '~/shared/components/inline-state/inline-state';
import { getFinanceTone } from '~/shared/format/finance-tone';
import { useFormat } from '~/shared/format/use-format';
import type { PortfolioBalance } from './portfolio-balances';
import styles from './portfolio-overview.module.css';

interface OverviewProps {
  query: ReturnType<typeof usePositions>;
  balances: PortfolioBalance[];
  onOpenPosition?: (position: Position) => void;
  highlight?: PositionHighlight;
}

export function PortfolioOverview({ query, balances, onOpenPosition, highlight }: OverviewProps) {
  const { t } = useLingui();
  if (query.isPending) return <Skeleton h={140} aria-label={t`Loading portfolio overview`} />;
  if (query.isError) {
    return (
      <InlineState
        className={styles.state}
        error
        action={
          <Button variant="default" onClick={() => void query.refetch()}>
            <Trans>Try again</Trans>
          </Button>
        }>
        <Trans>Could not load the portfolio balance.</Trans>
      </InlineState>
    );
  }
  if (!balances.length)
    return (
      <InlineState className={styles.state}>
        <Trans>Record a trade to see your balance and allocation.</Trans>
      </InlineState>
    );

  return (
    <section className={styles.hero} aria-label={onOpenPosition ? t`Portfolio balance and allocation` : t`Portfolio balance`}>
      {balances.map((balance) => (
        <div className={styles.currency} key={balance.currency}>
          <Balance balance={balance} />
          {onOpenPosition && <Allocation balance={balance} onOpenPosition={onOpenPosition} highlight={highlight} />}
        </div>
      ))}
    </section>
  );
}

function Balance({ balance }: { balance: PortfolioBalance }) {
  const { t } = useLingui();
  const format = useFormat();
  const { currency, total, profit, cost, returnPercentage, unquoted, hasQuotes } = balance;
  const tone = getFinanceTone(profit);

  return (
    <div className={styles.balance}>
      <dl>
        <div>
          <dt>
            {unquoted ? t`Quoted balance` : t`Portfolio balance`} · {currency}
          </dt>
          <dd className={styles.balanceValue}>{hasQuotes ? format.toCurrency(total, false, currency) : '—'}</dd>
        </div>
        {hasQuotes && (
          <div className={styles.return}>
            <dt>
              <Trans>Unrealized P&L</Trans>
            </dt>
            <dd>
              {returnPercentage != null && (
                <FinanceBadge tone={tone}>{format.toLocalePercentage(returnPercentage, 2, { signDisplay: 'exceptZero' })}</FinanceBadge>
              )}
              <FinanceBadge variant="text" tone={tone}>
                {format.currency(profit, { currency, signDisplay: 'exceptZero' })}
              </FinanceBadge>
            </dd>
          </div>
        )}
        <div className={styles.cost}>
          <dt>
            <Trans>Cost basis</Trans>
          </dt>
          <dd>{format.toCurrency(cost, false, currency)}</dd>
        </div>
      </dl>
      {unquoted > 0 && (
        <p className={styles.state}>
          <Plural value={unquoted} one="# position has no current quote." other="# positions have no current quote." />
        </p>
      )}
    </div>
  );
}

export function PortfolioAllocation({
  balances,
  onOpenPosition,
  highlight
}: {
  balances: PortfolioBalance[];
  onOpenPosition: (position: Position) => void;
  highlight: PositionHighlight;
}) {
  const allocated = balances.filter(({ segments }) => segments.length > 0);
  if (!allocated.length) return null;
  return (
    <div className={styles.allocationGroup}>
      {allocated.map((balance) => (
        <Allocation key={balance.currency} balance={balance} onOpenPosition={onOpenPosition} highlight={highlight} />
      ))}
    </div>
  );
}

function Allocation({
  balance,
  onOpenPosition,
  highlight
}: {
  balance: PortfolioBalance;
  onOpenPosition: (position: Position) => void;
  highlight?: PositionHighlight;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const [expanded, setExpanded] = useState(false);
  const { currency, segments, total } = balance;
  if (!segments.length) return null;
  const entries = segments.map(({ position, value, returnPercentage, colorIndex, weight }) => ({
    position,
    value,
    returnPercentage,
    colorIndex,
    weight: format.toLocalePercentage(weight, 1),
    money: format.toCurrency(value, false, currency)
  }));

  return (
    <div className={styles.allocation} data-has-highlight={segments.some(({ position }) => position.id === highlight?.id) || undefined}>
      <div className={styles.allocationHeading}>
        <h2>
          <Trans>Asset allocation</Trans>
        </h2>
        <span>
          <Plural value={segments.length} one="# asset" other="# assets" /> · {currency}
        </span>
      </div>
      <fieldset className={styles.allocationBar} aria-label={t`${currency} allocation by quoted value`}>
        {entries.map(({ position, value, colorIndex, weight, money }) => {
          const label = `${position.instrument.symbol} · ${weight} · ${money}`;
          return (
            <Tooltip key={position.id} label={label} events={{ hover: true, focus: true, touch: false }}>
              <button
                type="button"
                style={{ flexGrow: value / total }}
                data-asset-color={colorIndex}
                aria-label={label}
                aria-haspopup="dialog"
                onClick={() => onOpenPosition(position)}
                {...getPositionHighlightProps(position.id, highlight)}
              />
            </Tooltip>
          );
        })}
      </fieldset>
      <ul className={styles.legend}>
        {(expanded ? entries : entries.slice(0, 4)).map(({ position, colorIndex, weight, money, returnPercentage }) => (
          <li key={position.id}>
            <button
              type="button"
              data-asset-color={colorIndex}
              aria-haspopup="dialog"
              onClick={() => onOpenPosition(position)}
              {...getPositionHighlightProps(position.id, highlight)}>
              <span className={styles.legendIdentity}>
                <span className={styles.dot} aria-hidden="true" />
                <strong>{position.instrument.symbol}</strong>
                <small>{weight}</small>
              </span>
              <span className={styles.legendValue}>
                {money}
                {returnPercentage != null && (
                  <FinanceBadge as="small" variant="text" tone={getFinanceTone(returnPercentage)}>
                    {format.toLocalePercentage(returnPercentage, 2, { signDisplay: 'exceptZero' })}
                  </FinanceBadge>
                )}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {entries.length > 4 && (
        <Button variant="subtle" size="compact-sm" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>
          {expanded ? <Trans>Show fewer assets</Trans> : <Trans>Show all assets</Trans>}
        </Button>
      )}
    </div>
  );
}
