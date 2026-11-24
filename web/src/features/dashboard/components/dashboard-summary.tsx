import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { IconChevronDown } from '@tabler/icons-react';
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
  const [allTime, setAllTime] = useState(true);
  const { currencyCode, totalBalance, dailyChange, realizedGains } = dashboard;
  const balance = totalBalance.value;
  const balanceCurrency = totalBalance.currencyCode ?? currencyCode;
  const balanceReturn = balance != null && totalBalance.cost != null ? balance - totalBalance.cost : undefined;
  const daily =
    dailyChange.currentValue != null && dailyChange.previousValue != null
      ? dailyChange.currentValue - dailyChange.previousValue
      : undefined;
  const profit = allTime ? realized.allTime : realizedGains.currentPeriod;
  const count = realized.recordedSaleCount;
  const winning = realized.winningSaleCount;

  function money(value: number | undefined, currency: string, signed = false) {
    if (value == null) return '—';
    return format.currency(value, {
      currency,
      currencyDisplay: currency === 'TRY' ? 'narrowSymbol' : undefined,
      signDisplay: signed ? 'exceptZero' : undefined
    });
  }

  function percent(value?: number) {
    return value == null ? '—' : format.toLocalePercentage(value, 2, { signDisplay: 'exceptZero' });
  }

  const dailyText = money(daily, dailyChange.currencyCode ?? currencyCode, true);
  const dailyPercent = percent(dailyChange.percentageChange);
  const today = t({ comment: 'Time label for the daily change in open position value', message: 'today' });
  const periodLabel = allTime ? t`All time` : t`This month`;
  const periodAction = allTime ? t`Show this month` : t`Show all time`;

  return (
    <dl className={styles.summary} aria-label={t`Dashboard summary`}>
      <div className={styles.balance}>
        <dt>
          <Trans>Total balance</Trans>
          <span className={styles.balanceCurrency}>{balanceCurrency}</span>
        </dt>
        <dd className={styles.balanceValue}>{money(balance, balanceCurrency)}</dd>
        <dd className={styles.balanceChange}>
          <span className="finance-badge" data-tone={tone(totalBalance.percentageChange)} title={t`Open positions versus cost basis`}>
            {percent(totalBalance.percentageChange)}
          </span>
          <span className={styles.balanceReturn}>
            {balanceReturn != null && <strong data-tone={tone(balanceReturn)}>{money(balanceReturn, balanceCurrency, true)}</strong>}
            <span>
              <Trans>all time</Trans>
            </span>
          </span>
          {(daily != null || dailyChange.percentageChange != null) && (
            <span className={styles.balanceDaily}>
              <strong data-tone={tone(daily ?? dailyChange.percentageChange)}>
                {daily == null ? dailyPercent : dailyText}
                {daily != null && dailyChange.percentageChange != null && ` (${dailyPercent})`}
              </strong>
              <span>{today}</span>
            </span>
          )}
        </dd>
      </div>
      <div className={styles.daily}>
        <dt>
          <Trans>Daily change</Trans>
        </dt>
        <dd className={styles.metricValue} data-tone={tone(daily)}>
          {dailyText}
        </dd>
        <dd className={styles.metricDetail} title={t`vs. previous close`}>
          {dailyChange.percentageChange != null && <strong data-tone={tone(dailyChange.percentageChange)}>{dailyPercent}</strong>}
          {today}
        </dd>
      </div>
      <div className={styles.realized}>
        <dt>
          <Trans>Realized P&L</Trans>
        </dt>
        <dd className={styles.metricValue} data-tone={tone(profit)}>
          {money(profit, allTime ? currencyCode : (realizedGains.currencyCode ?? currencyCode), true)}
          {allTime && profit != null && !realized.complete && (
            <span className={styles.estimate}>
              {' '}
              <Trans comment="Realized profit and loss total includes only sales with reported values">Partial</Trans>
            </span>
          )}
        </dd>
        <dd className={styles.metricDetail}>
          <button
            type="button"
            className={styles.periodButton}
            aria-label={periodAction}
            title={periodAction}
            onClick={() => setAllTime(!allTime)}>
            {periodLabel}
            <IconChevronDown size={12} stroke={1.8} aria-hidden="true" />
          </button>
        </dd>
      </div>
      <div className={styles.win}>
        <dt>
          <Trans>Win rate</Trans>
        </dt>
        <dd className={styles.metricValue}>{realized.winRate == null ? '—' : format.toLocalePercentage(realized.winRate, 1)}</dd>
        <dd className={styles.metricDetail}>
          {count == null ? (
            '—'
          ) : (
            <Plural
              comment="Share of recorded sales that closed with a profit. The winning number is the numerator."
              value={count}
              _0="No recorded sales"
              one={`${winning} / # profitable sale`}
              other={`${winning} / # profitable sales`}
            />
          )}
        </dd>
      </div>
    </dl>
  );
}
