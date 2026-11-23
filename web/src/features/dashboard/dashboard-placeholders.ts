import { msg } from '@lingui/core/macro';
import dayjs from 'dayjs';
import type { components } from '~/api/schema';
import type { createFormat } from '~/shared/format/format';

type Trade = components['schemas']['TransactionInfo'];
type Portfolio = components['schemas']['BasicPortfolioView'];
type Position = components['schemas']['PositionInfo'];

export type ChartRange = '3M' | '6M' | 'All';
export type PerformanceGrouping = 'day' | 'week' | 'month';

type DateFormatter = ReturnType<typeof createFormat>['date'];
type PerformanceValues = { date: number; periodPnl: number; tradeCount: number; cumulative: number };
export type PerformancePoint = PerformanceValues & { tickLabel: string };

export function getRealizedHistory(trades?: Trade[]) {
  const sales = trades?.filter(({ type }) => type === 'SELL');
  const recorded = sales?.flatMap((sale) => (sale.profit == null ? [] : [{ ...sale, profit: sale.profit }])) ?? [];
  const count = recorded.length;

  // TODO(backend): Replace the sum and coverage check with DashboardView.realizedGains.allTime and its coverage fields.
  const allTime = sales?.length === 0 || count > 0 ? recorded.reduce((sum, sale) => sum + sale.profit, 0) : undefined;
  return {
    recorded,
    allTime,
    complete: sales != null && count === sales.length,
    winRate: count > 0 ? (recorded.filter(({ profit }) => profit > 0).length / count) * 100 : undefined,
    recordedSaleCount: trades == null ? undefined : count
  };
}

export function getPerformanceSeries(
  recorded: ReturnType<typeof getRealizedHistory>['recorded'],
  selectedRange: ChartRange | undefined,
  formatDate: DateFormatter,
  now = dayjs()
) {
  // TODO(backend): Replace client-side date windowing and aggregation with the range-aware performance endpoint.
  const sixMonthsAgo = now.subtract(6, 'month').startOf('day').valueOf();
  const today = now.endOf('day').valueOf();
  const days = new Map<number, { periodPnl: number; tradeCount: number }>();

  for (const sale of recorded) {
    const date = dayjs(sale.actionDate).startOf('day').valueOf();
    if (!Number.isFinite(date)) continue;
    const day = days.get(date) ?? { periodPnl: 0, tradeCount: 0 };
    day.periodPnl += sale.profit;
    day.tradeCount += 1;
    days.set(date, day);
  }

  const ordered = [...days].sort(([a], [b]) => a - b);
  const range = selectedRange ?? (ordered.some(([date]) => date >= sixMonthsAgo && date <= today) ? '6M' : 'All');
  const firstDay = range === '3M' ? now.subtract(3, 'month').startOf('day').valueOf() : sixMonthsAgo;
  const inRange = range === 'All' ? ordered : ordered.filter(([date]) => date >= firstDay && date <= today);
  const periodicGrouping: PerformanceGrouping = range !== '3M' ? 'month' : inRange.length < 6 ? 'day' : 'week';
  const buckets = new Map<string, PerformanceValues & { key: string }>();
  let count = 0;
  let cumulative = 0;
  const daily = inRange.map(([date, day]) => {
    cumulative += day.periodPnl;
    count += day.tradeCount;
    const start = periodStart(date, periodicGrouping);
    const key = start.format(periodicGrouping === 'month' ? 'YYYY-MM' : 'YYYY-MM-DD');
    const bucket = buckets.get(key) ?? { key, date: start.valueOf(), periodPnl: 0, tradeCount: 0, cumulative: 0 };
    bucket.periodPnl += day.periodPnl;
    bucket.tradeCount += day.tradeCount;
    bucket.cumulative = cumulative;
    buckets.set(key, bucket);
    return { date, ...day, cumulative };
  });

  return {
    range,
    count,
    cumulative,
    cumulativePoints: labelPoints(daily, 'day', formatDate),
    periodicPoints: labelPoints([...buckets.values()], periodicGrouping, formatDate),
    periodicGrouping
  };
}

function periodStart(date: number, grouping: PerformanceGrouping) {
  const day = dayjs(date);
  if (grouping === 'month') return day.startOf('month');
  // Monday-to-Sunday buckets stay stable when the interface language changes.
  if (grouping === 'week') return day.subtract((day.day() + 6) % 7, 'day').startOf('day');
  return day;
}

function labelPoints<T extends PerformanceValues>(points: T[], grouping: PerformanceGrouping, formatDate: DateFormatter) {
  const first = points[0];
  const last = points[points.length - 1];
  const spansMultipleYears = first && last && new Date(first.date).getFullYear() !== new Date(last.date).getFullYear();
  const options: Intl.DateTimeFormatOptions = { month: 'short', day: grouping === 'month' ? undefined : 'numeric' };

  return points.map((point) => {
    const label = formatDate(point.date, options);
    const tickLabel = spansMultipleYears ? `${label} ’${formatDate(point.date, { year: '2-digit' })}` : label;
    return { ...point, tickLabel };
  });
}

function describePosition(position: Position) {
  const { instrument, quantity, total } = position;
  // TODO(backend): Replace native valuation and cost-basis estimate with value and valueCurrencyCode from the dashboard summary.
  const estimatedValue = instrument.last == null;
  const value = instrument.last == null ? total : instrument.last * quantity;
  // TODO(backend): Replace full-name fallback with instrument.shortName when provided.
  const company = instrument.name || instrument.symbol;
  // TODO(backend): Use dailyImpact and dailyImpactPercentage with quote coverage instead of a flat placeholder for missing changes.
  const change = instrument.dailyChange;
  const estimatedDaily = change == null;
  const dailyAmount = (change ?? 0) * quantity;
  const previous = instrument.last != null && change != null ? instrument.last - change : undefined;
  const dailyPercentage = change == null ? 0 : previous != null && previous > 0 ? (change / previous) * 100 : undefined;

  return { position, company, value, estimatedValue, dailyAmount, dailyPercentage, estimatedDaily };
}

type PositionSummary = ReturnType<typeof describePosition>;

function summarizePortfolio(positions: PositionSummary[], currency: string) {
  const currencies = [...new Set(positions.map(({ position }) => position.currencyCode))].sort();
  const hasReportCurrency = currencies.includes(currency);
  const valueCurrency = hasReportCurrency ? currency : (currencies[0] ?? currency);
  const comparable = positions.filter(({ position }) => position.currencyCode === valueCurrency);
  const partial = currencies.length > 1;
  const value = comparable.reduce((sum, item) => sum + item.value, 0);
  const estimated = comparable.some((item) => item.estimatedValue);
  const basis = comparable.reduce((sum, { position }) => sum + position.total, 0);

  // TODO(backend): Replace open-position aggregation with linked portfolio assetCount, converted marketValue and returnPercentage.
  return {
    assetCount: new Set(positions.map(({ position }) => position.instrument.id)).size,
    value,
    valueCurrency,
    partial,
    estimated,
    returnPercentage: !partial && !estimated && basis > 0 ? ((value - basis) / basis) * 100 : undefined
  };
}

function getMarketStatus(positions: PositionSummary[]) {
  // TODO(backend): Replace position timestamps and the generic label with the dashboard marketQuotes summary and exchange code.
  const latestQuote = positions.reduce((latest, { position }) => Math.max(latest, Date.parse(position.instrument.updatedAt ?? '') || 0), 0);
  return {
    label: msg({ comment: 'Generic market label used when the exchange is unknown', message: 'Market' }),
    updatedAt: latestQuote > 0 ? latestQuote : undefined
  };
}

export function getDashboardDetails({
  trades,
  portfolios,
  positions,
  currency
}: {
  trades?: Trade[];
  portfolios?: Portfolio[];
  positions?: Position[];
  currency: string;
}) {
  // TODO(backend): Replace transaction-evidenced membership with DashboardView.linkedPortfolios, including portfolios with zero trades.
  const ids = new Set(trades?.map(({ portfolioId }) => portfolioId));
  const linked = portfolios?.filter(({ id }) => ids.has(id)) ?? [];
  const byPortfolio = new Map<string, PositionSummary[]>();
  const positionRows = (positions ?? []).filter(({ portfolio }) => ids.has(portfolio.id)).map(describePosition);
  for (const item of positionRows) {
    const id = item.position.portfolio.id;
    const group = byPortfolio.get(id) ?? [];
    group.push(item);
    byPortfolio.set(id, group);
  }

  const realized = new Map<string, number>();
  for (const { portfolioId, type, profit } of trades ?? []) {
    if (type === 'SELL' && profit != null) realized.set(portfolioId, (realized.get(portfolioId) ?? 0) + profit);
  }

  const rows = linked.map((portfolio) => ({
    ...portfolio,
    summary: positions == null ? undefined : summarizePortfolio(byPortfolio.get(portfolio.id) ?? [], currency),
    realized: realized.get(portfolio.id)
  }));

  // TODO(backend): Replace same-currency weights and position ranking with dashboard-currency portfolio weights and top positions.
  const denominator = rows.reduce((sum, { summary }) => sum + (summary?.valueCurrency === currency ? summary.value : 0), 0);
  const weighted = rows.map((row) => ({
    ...row,
    weight: denominator > 0 && row.summary?.valueCurrency === currency ? (row.summary.value / denominator) * 100 : undefined
  }));
  const weightEstimated = rows.some(({ summary }) => summary?.valueCurrency === currency && summary.estimated);
  const weightPartial = rows.some(({ summary }) => summary != null && (summary.partial || summary.valueCurrency !== currency));
  const rankingCurrency = positionRows.some(({ position }) => position.currencyCode === currency)
    ? currency
    : ([...new Set(positionRows.map(({ position }) => position.currencyCode))].sort()[0] ?? currency);
  const topPositions = positionRows
    .filter(({ position }) => position.currencyCode === rankingCurrency)
    .sort((a, b) => b.value - a.value)
    .slice(0, 3);

  return {
    portfolios: weighted,
    knownPortfolioCount: portfolios && trades ? linked.length : undefined,
    currency,
    weightEstimated,
    weightPartial,
    topPositions,
    rankingCurrency,
    market: getMarketStatus(positionRows)
  };
}

export type DashboardDetails = ReturnType<typeof getDashboardDetails>;
