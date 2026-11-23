import { Button, SegmentedControl, Select, Skeleton } from '@mantine/core';
import dayjs from 'dayjs';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { format } from '~/shared/format/format';
import styles from './earnings-overview.module.css';
import { PeriodNavigator } from './period-navigator';

type Period = 'monthly' | 'weekly';

export function EarningsOverview({ portfolioId }: { portfolioId: number }) {
  const now = dayjs();
  const [period, setPeriod] = useState<Period>('monthly');
  const [year, setYear] = useState(now.year());
  const [week, setWeek] = useState(now.startOf('week'));
  const [selectedCurrency, selectCurrency] = useState<string>();
  const [selectedBar, selectBar] = useState<number>();
  const trades = $api.useQuery('get', '/api/trades', { params: { query: { portfolioId } } });

  if (trades.isPending) return <Skeleton h={150} aria-label="Loading earnings" />;
  if (trades.isError)
    return (
      <div className={styles.state}>
        <p role="alert">Could not load earnings.</p>
        <Button variant="default" onClick={() => void trades.refetch()}>
          Try again
        </Button>
      </div>
    );

  const realized = trades.data.flatMap(({ type, profit, actionDate, position }) =>
    type === 'SELL' && profit != null ? [{ profit, date: dayjs(actionDate), currency: position.currencyCode }] : []
  );
  const currencies = [...new Set(realized.map(({ currency }) => currency))].sort();
  const currency = currencies.find((value) => value === selectedCurrency) ?? currencies[0];
  if (!currency) return <p className={styles.state}>Earnings will appear here after a sale with recorded P&L.</p>;

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
    label: start.add(index, unit).format(monthly ? 'MMM' : 'ddd'),
    profit: values.get(index)
  }));
  const total = [...values.values()].reduce((sum, profit) => sum + profit, 0);
  const max = Math.max(1, ...[...values.values()].map(Math.abs));
  const change = previous !== 0 ? ((total - previous) / Math.abs(previous)) * 100 : undefined;
  const active = bars[selectedBar ?? -1];
  const earliest = relevant.reduce((earlier, { date }) => (date.isBefore(earlier) ? date : earlier), now);
  const weekLabel = `${week.format('MMM D')} – ${week.add(6, 'day').format('MMM D')}`;
  const money = (value: number) => format.toCurrency(value, false, currency);

  function shift(offset: number) {
    if (monthly) setYear((value) => value + offset);
    else setWeek((value) => value.add(offset, 'week'));
    selectBar(undefined);
  }

  return (
    <div className={styles.view}>
      <div className={styles.controls}>
        <PeriodNavigator
          label={monthly ? String(year) : weekLabel}
          onPrev={() => shift(-1)}
          onNext={() => shift(1)}
          canPrev={monthly ? year > earliest.year() : week.isAfter(earliest.startOf('week'))}
          canNext={monthly ? year < now.year() : week.isBefore(now.startOf('week'))}
        />
        <SegmentedControl
          aria-label="Earnings period"
          size="xs"
          value={period}
          onChange={(value) => {
            setPeriod(value === 'weekly' ? 'weekly' : 'monthly');
            selectBar(undefined);
          }}
          data={[
            { label: 'Weekly', value: 'weekly' },
            { label: 'Monthly', value: 'monthly' }
          ]}
        />
        {currencies.length > 1 && (
          <Select
            aria-label="Earnings currency"
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
      {values.size ? (
        <>
          <div className={styles.summary}>
            <strong>{money(total)}</strong>
            {change != null && (
              <span className="finance-badge" data-tone={change > 0 ? 'profit' : change < 0 ? 'loss' : undefined}>
                {change > 0 ? '+' : ''}
                {format.toLocalePercentage(change, 1)} vs previous period
              </span>
            )}
          </div>
          <fieldset className={styles.chart} data-monthly={monthly || undefined} aria-label="Realized profit and loss by period">
            {bars.map(({ index, label, profit }) => (
              <button
                type="button"
                key={label}
                className={styles.column}
                aria-label={`${label}, ${profit == null ? 'no recorded P&L' : money(profit)}`}
                aria-pressed={index === selectedBar}
                onPointerEnter={(event) => {
                  if (event.pointerType === 'mouse') selectBar(index);
                }}
                onFocus={() => selectBar(index)}
                onClick={() => selectBar(index)}>
                <span className={styles.plot}>
                  {profit != null && (
                    <span
                      className={styles.bar}
                      data-loss={profit < 0 || undefined}
                      style={{ blockSize: `${Math.max(2, (Math.abs(profit) / max) * 48)}%` }}
                    />
                  )}
                </span>
                <span className={styles.label}>{label}</span>
              </button>
            ))}
          </fieldset>
          {active && (
            <p className={styles.detail}>
              {active.label} · {active.profit == null ? 'No recorded P&L' : money(active.profit)}
            </p>
          )}
        </>
      ) : (
        <p className={styles.state}>No realized P&L in this period.</p>
      )}
    </div>
  );
}
