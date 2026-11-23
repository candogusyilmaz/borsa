import { useState } from 'react';
import { $api } from '~/api/openapi';
import { usePositions } from '~/features/positions';
import { useFormat } from '~/shared/format/use-format';
import { type ChartRange, getDashboardDetails, getPerformanceSeries, getRealizedHistory } from '../dashboard-placeholders';

export function useDashboardView() {
  const { data: dashboards } = $api.useSuspenseQuery('get', '/api/dashboards');
  const [selectedId, setSelectedId] = useState<string>();
  const [selectedRange, setChartRange] = useState<ChartRange>();
  const format = useFormat();
  const selectable = dashboards.filter(({ id }) => !!id);
  const selected = selectable.find(({ id }) => id === selectedId) ?? selectable.find(({ isDefault }) => isDefault) ?? selectable[0];
  const params = { path: { dashboardId: Number(selected?.id) } };
  const dashboard = $api.useQuery('get', '/api/dashboards/{dashboardId}', { params }, { enabled: !!selected?.id });
  const trades = $api.useQuery('get', '/api/dashboards/{dashboardId}/transactions', { params }, { enabled: !!selected?.id });
  const portfolios = $api.useQuery('get', '/api/portfolios', {});
  const positions = usePositions();
  const currency = dashboard.data?.currencyCode;
  const realized = getRealizedHistory(trades.data);
  const chart = getPerformanceSeries(realized.recorded, selectedRange, format.date);
  const details = currency
    ? getDashboardDetails({ trades: trades.data, portfolios: portfolios.data, positions: positions.data, currency })
    : undefined;

  return {
    dashboards: selectable,
    selected,
    setSelectedId,
    dashboard,
    trades,
    positions,
    portfolios,
    realized,
    details,
    setChartRange,
    chart
  };
}

export type DashboardModel = ReturnType<typeof useDashboardView>;
