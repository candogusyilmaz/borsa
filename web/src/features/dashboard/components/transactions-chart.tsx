import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, SegmentedControl, Skeleton, VisuallyHidden } from '@mantine/core';
import { useId, useState } from 'react';
import { Area, Bar, CartesianGrid, ComposedChart, Rectangle, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useFormat } from '~/shared/format/use-format';
import type { ChartRange, PerformanceGrouping, PerformancePoint } from '../dashboard-placeholders';
import { tone } from '../dashboard-presentation';
import type { DashboardModel } from '../hooks/use-dashboard-view';
import styles from '../pages/dashboard-page.module.css';

type ChartMode = 'cumulative' | 'periodic';

const profitColor = 'var(--app-finance-profit)';
const lossColor = 'var(--app-finance-loss)';
const axisColor = 'var(--app-border-strong)';

export function TransactionsChart({ model, currency }: { model: DashboardModel; currency: string }) {
  const { t } = useLingui();
  const format = useFormat();
  const [mode, setMode] = useState<ChartMode>('cumulative');
  const { chart, trades } = model;
  const cumulative = format.toCurrency(chart.cumulative, false, currency);
  const points = mode === 'cumulative' ? chart.cumulativePoints : chart.periodicPoints;
  const grouping = mode === 'cumulative' ? 'day' : chart.periodicGrouping;
  const periodicLabel = chart.periodicGrouping === 'month' ? t`Monthly` : chart.periodicGrouping === 'week' ? t`Weekly` : t`Periodic`;

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
                  {' · '}
                  <Trans comment="Chart summary. The highlighted amount is the cumulative realized profit or loss in the selected range.">
                    <strong className={styles.chartTotal} data-tone={tone(chart.cumulative)}>
                      {cumulative}
                    </strong>{' '}
                    cumulative
                  </Trans>
                </>
              )}
            </output>
          )}
        </div>
        <div className={styles.chartControls}>
          <SegmentedControl<ChartRange>
            size="xs"
            aria-label={t`Performance period`}
            value={chart.range}
            onChange={model.setChartRange}
            data={[
              { value: '3M', label: t`3M` },
              { value: '6M', label: t`6M` },
              { value: 'All', label: t`All` }
            ]}
          />
          <SegmentedControl<ChartMode>
            size="xs"
            aria-label={t`Chart view`}
            value={mode}
            onChange={setMode}
            data={[
              { value: 'cumulative', label: t`Cumulative` },
              { value: 'periodic', label: periodicLabel }
            ]}
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
        (points.length > 0 ? (
          <PerformancePlot points={points} currency={currency} mode={mode} grouping={grouping} />
        ) : (
          <p className={styles.chartEmpty}>
            <Trans>No realized sales in this period.</Trans>
          </p>
        ))}
    </section>
  );
}

function PerformancePlot({
  points,
  currency,
  mode,
  grouping
}: {
  points: PerformancePoint[];
  currency: string;
  mode: ChartMode;
  grouping: PerformanceGrouping;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const id = useId();
  const fillId = `${id}-fill`;
  const strokeId = `${id}-stroke`;
  const helpId = `${id}-help`;
  const isCumulative = mode === 'cumulative';
  let max = 0;
  let min = 0;
  if (isCumulative) {
    for (const { cumulative } of points) {
      max = Math.max(max, cumulative);
      min = Math.min(min, cumulative);
    }
  }

  const zeroOffset = max === min ? 0 : max / (max - min);
  const mixed = max > 0 && min < 0;
  const lineColor = mixed ? `url(#${strokeId})` : max > 0 ? profitColor : min < 0 ? lossColor : axisColor;
  const chartLabel = isCumulative ? t`Cumulative realized profit and loss` : t`Realized profit and loss by period`;

  return (
    <div className={styles.chart}>
      <VisuallyHidden id={helpId}>
        <Trans>Use the left and right arrow keys to browse chart values.</Trans>
      </VisuallyHidden>
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <ComposedChart
          aria-label={chartLabel}
          aria-describedby={helpId}
          accessibilityLayer
          data={points}
          margin={{ top: 8, right: 6, bottom: 0, left: 0 }}>
          {isCumulative && (
            <defs>
              {mixed && (
                <linearGradient id={strokeId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset={0} stopColor={profitColor} />
                  <stop offset={zeroOffset} stopColor={profitColor} />
                  <stop offset={zeroOffset} stopColor={lossColor} />
                  <stop offset={1} stopColor={lossColor} />
                </linearGradient>
              )}
              <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
                {max > 0 && <stop offset={0} stopColor={profitColor} stopOpacity={0.22} />}
                {max > 0 && <stop offset={zeroOffset} stopColor={profitColor} stopOpacity={0.01} />}
                {min < 0 && <stop offset={zeroOffset} stopColor={lossColor} stopOpacity={0.01} />}
                {min < 0 && <stop offset={1} stopColor={lossColor} stopOpacity={0.22} />}
              </linearGradient>
            </defs>
          )}
          <CartesianGrid vertical={false} stroke={axisColor} strokeOpacity={0.35} strokeDasharray="3 3" />
          <XAxis
            dataKey="tickLabel"
            type="category"
            scale={isCumulative ? 'point' : 'band'}
            interval="preserveStartEnd"
            minTickGap={36}
            padding={{ left: 8, right: 8 }}
            tick={{ fill: 'var(--mantine-color-dimmed)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            width="auto"
            domain={['auto', 'auto']}
            tickFormatter={(value: number) => format.toCurrency(value, true, currency, 0, 1)}
            tick={{ fill: 'var(--mantine-color-dimmed)', fontSize: 11.5 }}
            tickLine={false}
            axisLine={false}
            tickCount={4}
          />
          <ReferenceLine y={0} ifOverflow="extendDomain" stroke={axisColor} strokeDasharray="4 4" strokeOpacity={0.8} />
          <Tooltip
            isAnimationActive={false}
            cursor={isCumulative ? { stroke: axisColor } : { fill: 'var(--app-surface-secondary)', fillOpacity: 0.55 }}
            content={({ active, payload }) => {
              const point = payload[0]?.payload as PerformancePoint | undefined;
              return active && point ? <PerformanceTooltip point={point} currency={currency} grouping={grouping} /> : null;
            }}
          />
          {isCumulative ? (
            <Area
              type="linear"
              dataKey="cumulative"
              name={t`Cumulative`}
              fill={max === min ? 'none' : `url(#${fillId})`}
              fillOpacity={1}
              stroke={lineColor}
              strokeWidth={2}
              baseValue={0}
              dot={points.length === 1}
              activeDot={{ r: 4, strokeWidth: 0, fill: 'var(--mantine-color-text)' }}
              isAnimationActive={false}
            />
          ) : (
            <Bar
              dataKey="periodPnl"
              name={t`Period P&L`}
              maxBarSize={28}
              isAnimationActive={false}
              shape={({ value, x, y, width, height }) => {
                const negative = (typeof value === 'number' ? value : value[1]) < 0;
                // Recharts gives losses a signed height. Normalize it so the rounded corners stay at the value end.
                return (
                  <Rectangle
                    x={x}
                    y={Math.min(y, y + height)}
                    width={width}
                    height={Math.abs(height)}
                    fill={negative ? lossColor : profitColor}
                    fillOpacity={0.82}
                    radius={negative ? [0, 0, 4, 4] : [4, 4, 0, 0]}
                  />
                );
              }}
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

function PerformanceTooltip({ point, currency, grouping }: { point: PerformancePoint; currency: string; grouping: PerformanceGrouping }) {
  const { t } = useLingui();
  const format = useFormat();
  const date = format.toShortDate(new Date(point.date));
  const title =
    grouping === 'month' ? format.date(point.date, { month: 'long', year: 'numeric' }) : grouping === 'week' ? t`Week of ${date}` : date;

  return (
    <output className={styles.tooltip}>
      <time dateTime={new Date(point.date).toISOString()}>{title}</time>
      <span className={styles.tooltipRow}>
        <span>
          <Trans>Period P&L</Trans>
        </span>
        <strong data-tone={tone(point.periodPnl)}>{format.toCurrency(point.periodPnl, false, currency)}</strong>
      </span>
      <span className={styles.tooltipRow}>
        <span>
          <Trans>Cumulative P&L</Trans>
        </span>
        <strong data-tone={tone(point.cumulative)}>{format.toCurrency(point.cumulative, false, currency)}</strong>
      </span>
      <small className={styles.tooltipCount}>
        <Plural value={point.tradeCount} one="# transaction" other="# transactions" />
      </small>
    </output>
  );
}
