import { SegmentedControl } from '@mantine/core';
import { useState } from 'react';
import type { components } from '~/api/schema';
import { format } from '~/shared/format/format';
import { signedPercentage, tone } from '../dashboard-presentation';
import type { DashboardModel } from '../hooks/use-dashboard-view';
import styles from '../pages/dashboard-page.module.css';

export function DashboardSummary({
  dashboard,
  realized
}: {
  dashboard: components['schemas']['DashboardView'];
  realized: DashboardModel['realized'];
}) {
  const [realizedPeriod, setRealizedPeriod] = useState<'All time' | 'This month'>('This month');
  const { currencyCode, totalBalance, dailyChange, realizedGains } = dashboard;
  const daily =
    dailyChange.currentValue != null && dailyChange.previousValue != null
      ? dailyChange.currentValue - dailyChange.previousValue
      : undefined;
  const allTime = realizedPeriod === 'All time';
  const profit = allTime ? realized.allTime : realizedGains.currentPeriod;
  const balance = totalBalance.value;
  const money = (value: number, currency = currencyCode) => format.toCurrency(value, false, currency);
  const count = realized.recordedSaleCount;

  return (
    <dl className={styles.summary} aria-label="Dashboard summary">
      <div className={styles.balance}>
        <dt>Total balance</dt>
        <dd>{balance == null ? '—' : money(balance, totalBalance.currencyCode)}</dd>
        <Change value={totalBalance.percentageChange} detail="all time" description="Open positions versus cost basis" />
      </div>
      <div>
        <dt>Daily change</dt>
        <dd data-tone={tone(daily)}>
          {daily == null ? '—' : format.currency(daily, { currency: dailyChange.currencyCode ?? currencyCode, signDisplay: 'exceptZero' })}
        </dd>
        <Change value={dailyChange.percentageChange} detail="vs. previous close" />
      </div>
      <div>
        <dt>Realized P&L</dt>
        <dd data-tone={tone(profit)}>
          {profit == null ? '—' : money(profit, allTime ? currencyCode : realizedGains.currencyCode)}
          {allTime && profit != null && !realized.complete && <span className={styles.estimate}> Partial</span>}
        </dd>
        <dd className={styles.metricDetail}>
          <SegmentedControl<'All time' | 'This month'>
            size="xs"
            className={styles.period}
            aria-label="Realized profit and loss period"
            value={realizedPeriod}
            onChange={setRealizedPeriod}
            data={['All time', 'This month']}
          />
        </dd>
      </div>
      <div>
        <dt>Win rate</dt>
        <dd>{realized.winRate == null ? '—' : format.toLocalePercentage(realized.winRate, 1)}</dd>
        <dd className={styles.metricDetail}>
          {count == null ? '— recorded sales' : `${count} recorded ${count === 1 ? 'sale' : 'sales'}`}
        </dd>
      </div>
    </dl>
  );
}

function Change({ value, detail, description }: { value?: number; detail: string; description?: string }) {
  return (
    <dd className={styles.metricDetail}>
      <span className="finance-badge" data-tone={tone(value)} title={description}>
        {value == null ? '—' : signedPercentage(value)}
      </span>{' '}
      {detail}
    </dd>
  );
}
