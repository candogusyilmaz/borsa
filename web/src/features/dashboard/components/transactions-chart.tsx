import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, SegmentedControl, Skeleton, Switch } from '@mantine/core';
import { useId, useState } from 'react';
import { Area, Bar, CartesianGrid, ComposedChart, Rectangle, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useFormat } from '~/shared/format/use-format';
import type { ChartRange, getPerformanceSeries } from '../dashboard-placeholders';
import type { DashboardModel } from '../hooks/use-dashboard-view';
import styles from '../pages/dashboard-page.module.css';

type Points = ReturnType<typeof getPerformanceSeries>['points'];

export function TransactionsChart({ model, currency }: { model: DashboardModel; currency: string }) {
  const { t } = useLingui();
  const format = useFormat();
  const [dailyBars, setDailyBars] = useState(true);
  const { chart, trades } = model;
  const cumulative = format.toCurrency(chart.cumulative, false, currency);

  return (
    <section className={styles.chartSurface} aria-labelledby="performance-title">
      <div className={styles.chartHeader}>
        <div className={styles.sectionTitle}>
          <h2 id="performance-title">
            <Trans>Realized performance</Trans>
          </h2>
          {trades.isSuccess && (
            <output>
              <Plural value={chart.count} one="# transaction" other="# transactions" />
              {chart.count > 0 && (
                <>
                  {' '}
                  · <Trans>{cumulative} cumulative</Trans>
                </>
              )}
            </output>
          )}
        </div>
        <div className={styles.chartControls}>
          <SegmentedControl<ChartRange>
            className={styles.range}
            size="xs"
            aria-label={t`Performance period`}
            value={model.chartRange}
            onChange={model.setChartRange}
            data={[
              { value: '3M', label: t`3M` },
              { value: '6M', label: t`6M` },
              { value: 'All', label: t`All` }
            ]}
          />
          <Switch
            className={styles.dailySwitch}
            size="xs"
            color="gray.6"
            label={t`Daily bars`}
            checked={dailyBars}
            onChange={(event) => setDailyBars(event.currentTarget.checked)}
          />
        </div>
      </div>
      {trades.isPending && <Skeleton h={224} aria-label={t`Loading performance chart`} />}
      {trades.isError && (
        <div className={styles.state}>
          <p role="alert">
            <Trans>Could not load realized performance.</Trans>
          </p>
          <Button variant="default" onClick={() => void trades.refetch()}>
            <Trans>Try again</Trans>
          </Button>
        </div>
      )}
      {trades.isSuccess &&
        (chart.points.length > 0 ? (
          <PerformancePlot points={chart.points} currency={currency} dailyBars={dailyBars} />
        ) : (
          <p className={styles.chartEmpty}>
            <Trans>No realized sales in this period.</Trans>
          </p>
        ))}
    </section>
  );
}

function PerformancePlot({ points, currency, dailyBars }: { points: Points; currency: string; dailyBars: boolean }) {
  const { t } = useLingui();
  const format = useFormat();
  const gradient = useId();
  const isSingle = points.length === 1;
  const money = (value: number) => format.toCurrency(value, false, currency);

  return (
    <div className={styles.chart}>
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <ComposedChart
          aria-label={t`Daily and cumulative realized profit and loss. Use arrow keys to browse dates.`}
          accessibilityLayer
          data={points}
          margin={{ top: 8, right: 6, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--app-chart-line)" stopOpacity={0.22} />
              <stop offset="100%" stopColor="var(--app-chart-line)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke="var(--app-border-strong)" strokeOpacity={0.35} strokeDasharray="3 3" />
          <XAxis
            dataKey="date"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            ticks={isSingle ? [points[0]!.date] : undefined}
            padding={{ left: 8, right: 8 }}
            tickFormatter={(date: number) => format.date(date, { month: 'short', day: 'numeric' })}
            tick={{ fill: 'var(--mantine-color-dimmed)', fontSize: 12 }}
            minTickGap={28}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            width="auto"
            tickFormatter={(value: number) => format.toCurrency(value, true, currency, 0, 1)}
            tick={{ fill: 'var(--mantine-color-dimmed)', fontSize: 11.5 }}
            tickLine={false}
            axisLine={false}
            tickCount={4}
          />
          <Tooltip
            isAnimationActive={false}
            cursor={{ stroke: 'var(--app-border-strong)' }}
            content={({ active, payload }) => {
              const point = payload?.[0]?.payload as Points[number] | undefined;
              if (!active || !point) return null;

              return (
                <div className={styles.tooltip}>
                  <time dateTime={new Date(point.date).toISOString()}>{format.toShortDate(new Date(point.date))}</time>
                  <div>
                    <span>
                      <Trans>Cumulative</Trans>
                    </span>
                    <strong>{money(point.cumulative)}</strong>
                  </div>
                  <div>
                    <span>
                      <Trans>Daily P&L</Trans>
                    </span>
                    <strong data-tone={point.daily > 0 ? 'profit' : point.daily < 0 ? 'loss' : undefined}>{money(point.daily)}</strong>
                  </div>
                </div>
              );
            }}
          />
          <Area
            type="monotone"
            dataKey="cumulative"
            name={t`Cumulative`}
            fill={`url(#${gradient})`}
            fillOpacity={1}
            stroke="var(--app-chart-line)"
            strokeWidth={2}
            baseValue={0}
            dot={isSingle}
            activeDot={{ r: 4 }}
            isAnimationActive={false}
          />
          {dailyBars && (
            <Bar
              dataKey="daily"
              name={t`Daily P&L`}
              barSize={8}
              isAnimationActive={false}
              shape={(props) => (
                <Rectangle
                  {...props}
                  fill={props.payload.daily < 0 ? 'var(--app-finance-loss)' : 'var(--app-finance-profit)'}
                  fillOpacity={0.36}
                  radius={props.payload.daily < 0 ? [0, 0, 3, 3] : [3, 3, 0, 0]}
                />
              )}
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
