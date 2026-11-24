import { Trans, useLingui } from '@lingui/react/macro';
import { Button, SegmentedControl, Select, Skeleton } from '@mantine/core';
import dayjs from 'dayjs';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { useFormat } from '~/shared/format/use-format';
import styles from './earnings-overview.module.css';
import { PeriodNavigator } from './period-navigator';

type Period = 'monthly' | 'weekly';

export function EarningsOverview({ portfolioId }: { portfolioId: number }) {
  const { t } = useLingui();
  const format = useFormat();
  const now = dayjs();
  const [period, setPeriod] = useState<Period>('monthly');
  const [year, setYear] = useState(now.year());
  const [week, setWeek] = useState(now.startOf('week'));
  const [selectedCurrency, selectCurrency] = useState<string>();
  const [selectedBar, selectBar] = useState<number>();
  const trades = $api.useQuery('get', '/api/trades', { params: { query: { portfolioId } } });

  if (trades.isPending) return <Skeleton h={150} aria-label={t`Loading earnings`} />;
  if (trades.isError)
    return (
      <div className={styles.state}>
        <p role="alert">
          <Trans>Could not load earnings.</Trans>
        </p>
        <Button variant="default" onClick={() => void trades.refetch()}>
          <Trans>Try again</Trans>
        </Button>
      </div>
    );

  const realized = trades.data.flatMap(({ type, profit, actionDate, position }) =>
    type === 'SELL' && profit != null ? [{ profit, date: dayjs(actionDate), currency: position.currencyCode }] : []
  );
  const currencies = [...new Set(realized.map(({ currency }) => currency))].sort();
  const currency = currencies.find((value) => value === selectedCurrency) ?? currencies[0];
  if (!currency)
    return (
      <p className={styles.state}>
        <Trans>Earnings will appear here after a sale with recorded P&L.</Trans>
      </p>
    );

  const relevant = realized.filter((trade) => trade.currency === currency);
  const monthly = period === 'monthly';
  const start = monthly ? now.year(year).startOf('year') : week;
  const unit = monthly ? 'month' : 'day';
  const values = new Map<number, number>();
  let previous = 0;
  const previousStart = monthly ? start.subtract(1, 'year') : start.subtract(1, 'week');
  for (const { date, profit } of relevant) {
    if (date.isBefore(start) && !date.isBefore(previousStart)) previous += profit;
    if (!date.isBefore(start) && date.isBefore(start.add(1, monthly ? 'year' : 'week'))) {
      const index = date.diff(start, unit);
      values.set(index, (values.get(index) ?? 0) + profit);
    }
  }
  const bars = Array.from({ length: monthly ? 12 : 7 }, (_, index) => ({
    index,
    label: format.date(start.add(index, unit).valueOf(), monthly ? { month: 'short' } : { weekday: 'short' }),
    fullLabel: format.date(start.add(index, unit).valueOf(), monthly ? { month: 'long', year: 'numeric' } : { dateStyle: 'long' }),
    profit: values.get(index)
  }));
  const total = [...values.values()].reduce((sum, profit) => sum + profit, 0);
  const high = Math.max(0, ...values.values());
  const low = Math.min(0, ...values.values());
  const span = high - low;
  const baseline = span > 0 ? 4 + (-low / span) * 92 : 50;
  const change = previous !== 0 ? ((total - previous) / Math.abs(previous)) * 100 : undefined;
  const active = bars[selectedBar ?? Math.max(...values.keys())];
  const earliest = relevant.reduce((earlier, { date }) => (date.isBefore(earlier) ? date : earlier), now);
  const weekStart = format.date(week.valueOf(), { month: 'short', day: 'numeric' });
  const weekEnd = format.date(week.add(6, 'day').valueOf(), { month: 'short', day: 'numeric' });
  const weekLabel = `${weekStart} – ${weekEnd}`;
  const money = (value: number) => format.currency(value, { currency, signDisplay: 'exceptZero' });

  function shift(offset: number) {
    if (monthly) setYear((value) => value + offset);
    else setWeek((value) => value.add(offset, 'week'));
    selectBar(undefined);
  }

  return (
    <div className={styles.view}>
      <div className={styles.header}>
        {values.size > 0 && (
          <div className={styles.summary}>
            <span>
              <Trans>Realized P&L</Trans>
            </span>
            <strong data-tone={total < 0 ? 'loss' : undefined}>{format.toCurrency(total, false, currency)}</strong>
            {change != null && (
              <span className="finance-badge" data-tone={change > 0 ? 'profit' : change < 0 ? 'loss' : undefined}>
                <Trans>{format.toLocalePercentage(change, 1, { signDisplay: 'exceptZero' })} vs previous period</Trans>
              </span>
            )}
          </div>
        )}
        <div className={styles.controls}>
          <PeriodNavigator
            label={monthly ? format.number(year, { useGrouping: false }) : weekLabel}
            onPrev={() => shift(-1)}
            onNext={() => shift(1)}
            canPrev={monthly ? year > earliest.year() : week.isAfter(earliest.startOf('week'))}
            canNext={monthly ? year < now.year() : week.isBefore(now.startOf('week'))}
          />
          <SegmentedControl
            aria-label={t`Earnings period`}
            size="xs"
            value={period}
            onChange={(value) => {
              setPeriod(value === 'weekly' ? 'weekly' : 'monthly');
              selectBar(undefined);
            }}
            data={[
              { label: t`Weekly`, value: 'weekly' },
              { label: t`Monthly`, value: 'monthly' }
            ]}
          />
          {currencies.length > 1 && (
            <Select
              aria-label={t`Earnings currency`}
              value={currency}
              data={currencies}
              allowDeselect={false}
              onChange={(value) => {
                if (value) selectCurrency(value);
                selectBar(undefined);
              }}
              className={styles.currency}
            />
          )}
        </div>
      </div>
      {values.size ? (
        <div className={styles.canvas}>
          <fieldset className={styles.chart} data-monthly={monthly || undefined} aria-label={t`Realized profit and loss by period`}>
            {bars.map(({ index, label, fullLabel, profit }) => (
              <button
                type="button"
                key={index}
                className={styles.column}
                aria-label={`${fullLabel}, ${profit == null ? t`no recorded P&L` : money(profit)}`}
                aria-pressed={index === active?.index}
                onPointerEnter={(event) => {
                  if (event.pointerType === 'mouse') selectBar(index);
                }}
                onFocus={() => selectBar(index)}
                onClick={() => selectBar(index)}>
                <span className={styles.plot}>
                  <span aria-hidden="true" className={styles.zero} style={{ insetBlockEnd: `${baseline}%` }} />
                  <span
                    aria-hidden="true"
                    className={styles.bar}
                    data-tone={profit ? (profit > 0 ? 'profit' : 'loss') : undefined}
                    style={{
                      blockSize: profit ? `${(Math.abs(profit) / span) * 92}%` : '2px',
                      ...(profit != null && profit < 0 ? { insetBlockStart: `${100 - baseline}%` } : { insetBlockEnd: `${baseline}%` })
                    }}
                  />
                </span>
                <span className={styles.label}>{label}</span>
              </button>
            ))}
          </fieldset>
          {active && (
            <p className={styles.detail}>
              <span>{active.fullLabel}</span>
              <span data-tone={active.profit ? (active.profit > 0 ? 'profit' : 'loss') : undefined}>
                {active.profit == null ? t`No recorded P&L` : money(active.profit)}
              </span>
            </p>
          )}
        </div>
      ) : (
        <p className={styles.state}>
          <Trans>No realized P&L in this period.</Trans>
        </p>
      )}
    </div>
  );
}
