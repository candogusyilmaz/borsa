import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import type { components } from '~/api/schema';

type TradeInfo = components['schemas']['TradeInfo'];
export type UndoTrade = components['schemas']['Item'];
export type TradesScope = { portfolioId?: string; positionId?: never } | { positionId: string; portfolioId?: never };

interface TradeFilters {
  query: string;
  type: 'BUY' | 'SELL' | null;
  portfolio: string | null;
  pageSize: number;
}

const initialFilters: TradeFilters = { query: '', type: null, portfolio: null, pageSize: 10 };

export function useTradesList({ portfolioId, positionId }: TradesScope) {
  const [filters, setFilters] = useState(initialFilters);
  const [page, setPage] = useState(1);
  const trades = $api.useQuery(
    'get',
    '/api/trades',
    { params: { query: { portfolioId: portfolioId ? Number(portfolioId) : undefined } } },
    {
      enabled: !positionId
    }
  );
  const activeTrades = $api.useQuery(
    'get',
    '/api/positions/{positionId}/active-trades',
    {
      params: { path: { positionId: Number(positionId) } }
    },
    { enabled: !!positionId }
  );
  const result = positionId ? activeTrades : trades;
  const all = result.data ?? [];
  const query = filters.query.trim().toLowerCase();
  const filtered = all
    .filter((trade) => {
      const { instrumentSymbol, instrumentName, portfolio } = trade.position;
      return (
        (!filters.type || trade.type === filters.type) &&
        (!filters.portfolio || portfolio.id === filters.portfolio) &&
        (!query || [instrumentSymbol, instrumentName, portfolio.name].some((value) => value?.toLowerCase().includes(query)))
      );
    })
    .sort((a, b) => Date.parse(b.actionDate) - Date.parse(a.actionDate));
  const pages = Math.ceil(filtered.length / filters.pageSize);
  const currentPage = Math.max(1, Math.min(page, pages));
  const visible = positionId ? filtered : filtered.slice((currentPage - 1) * filters.pageSize, currentPage * filters.pageSize);

  const visiblePortfolioIds = [...new Set(visible.map(({ position }) => position.portfolio.id))];
  const histories = useQueries({
    queries: visiblePortfolioIds.map((id) =>
      $api.queryOptions('get', '/api/portfolios/{portfolioId}/trades', { params: { path: { portfolioId: Number(id) } } })
    )
  });
  const undo = new Map(histories.flatMap(({ data }) => (data?.trades ?? []).map((trade) => [trade.holdingId, trade] as const)));

  function update(next: Partial<TradeFilters>) {
    setFilters((filters) => ({ ...filters, ...next }));
    setPage(1);
  }

  return {
    result,
    visible,
    undo,
    filters,
    update,
    setPage,
    pages,
    currentPage,
    portfolioOptions: [...new Map(all.map(({ position }) => [position.portfolio.id, position.portfolio])).values()].map(({ id, name }) => ({
      value: id,
      label: name
    })),
    activeCount:
      Number(!!query) + Number(!!filters.type) + Number(!!filters.portfolio) + Number(filters.pageSize !== initialFilters.pageSize),
    reset: () => update(initialFilters),
    filteredCount: filtered.length
  };
}

export type TradeRow = TradeInfo;
export type TradesListModel = ReturnType<typeof useTradesList>;
