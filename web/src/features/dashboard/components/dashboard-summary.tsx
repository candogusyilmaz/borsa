import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { SegmentedControl } from '@mantine/core';
import { useState } from 'react';
import type { components } from '~/api/schema';
import { useFormat } from '~/shared/format/use-format';
import { tone } from '../dashboard-presentation';
import type { DashboardModel } from '../hooks/use-dashboard-view';
import styles from '../pages/dashboard-page.module.css';

export function DashboardSummary({
  dashboard,
  realized
}: {
  dashboard: components['schemas']['DashboardView'];
  realized: DashboardModel['realized'];
}) {
  const { t } = useLingui();
  const format = useFormat();
  const [realizedPeriod, setRealizedPeriod] = useState<'all' | 'month'>('month');
  const { currencyCode, totalBalance, dailyChange, realizedGains } = dashboard;
  const daily =
    dailyChange.currentValue != null && dailyChange.previousValue != null
      ? dailyChange.currentValue - dailyChange.previousValue
      : undefined;
  const allTime = realizedPeriod === 'all';
  const profit = allTime ? realized.allTime : realizedGains.currentPeriod;
  const balance = totalBalance.value;
  const money = (value: number, currency = currencyCode) => format.toCurrency(value, false, currency);
  const count = realized.recordedSaleCount;

  return (
    <dl className={styles.summary} aria-label={t`Dashboard summary`}>
      <div className={styles.balance}>
        <dt>
          <Trans>Total balance</Trans>
        </dt>
        <dd>{balance == null ? '—' : money(balance, totalBalance.currencyCode)}</dd>
        <Change value={totalBalance.percentageChange} detail={t`all time`} description={t`Open positions versus cost basis`} />
      </div>
      <div>
        <dt>
          <Trans>Daily change</Trans>
        </dt>
        <dd data-tone={tone(daily)}>
          {daily == null ? '—' : format.currency(daily, { currency: dailyChange.currencyCode ?? currencyCode, signDisplay: 'exceptZero' })}
        </dd>
        <Change value={dailyChange.percentageChange} detail={t`vs. previous close`} />
      </div>
      <div>
        <dt>
          <Trans>Realized P&L</Trans>
        </dt>
        <dd data-tone={tone(profit)}>
          {profit == null ? '—' : money(profit, allTime ? currencyCode : realizedGains.currencyCode)}
          {allTime && profit != null && !realized.complete && (
            <span className={styles.estimate}>
              {' '}
              <Trans comment="Realized profit and loss total includes only sales with reported values">Partial</Trans>
            </span>
          )}
        </dd>
        <dd className={styles.metricDetail}>
          <SegmentedControl<'all' | 'month'>
            size="xs"
            className={styles.period}
            aria-label={t`Realized profit and loss period`}
            value={realizedPeriod}
            onChange={setRealizedPeriod}
            data={[
              { value: 'all', label: t`All time` },
              { value: 'month', label: t`This month` }
            ]}
          />
        </dd>
      </div>
      <div>
        <dt>
          <Trans>Win rate</Trans>
        </dt>
        <dd>{realized.winRate == null ? '—' : format.toLocalePercentage(realized.winRate, 1)}</dd>
        <dd className={styles.metricDetail}>
          {count == null ? t`— recorded sales` : <Plural value={count} one="# recorded sale" other="# recorded sales" />}
        </dd>
      </div>
    </dl>
  );
}

function Change({ value, detail, description }: { value?: number; detail: string; description?: string }) {
  const format = useFormat();
  return (
    <dd className={styles.metricDetail}>
      <span className="finance-badge" data-tone={tone(value)} title={description}>
        {value == null ? '—' : format.toLocalePercentage(value, 2, { signDisplay: 'exceptZero' })}
      </span>{' '}
      {detail}
    </dd>
  );
}
