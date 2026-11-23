import { useState } from 'react';
import type { components } from '~/api/schema';
import { usePositions } from './use-positions';

export type Position = components['schemas']['PositionInfo'];
const pageSize = 10;

export function usePositionsView(portfolioId: number | undefined, onOpenPosition?: (position: Position) => void) {
  const query = usePositions(portfolioId);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState<string>();
  const allPositions = query.data ?? [];
  const term = search.trim().toLowerCase();
  const filtered = allPositions
    .filter(({ instrument, portfolio }) =>
      [instrument.symbol, instrument.name, portfolio.name].some((value) => value.toLowerCase().includes(term))
    )
    .sort((a, b) => a.instrument.symbol.localeCompare(b.instrument.symbol));
  const pages = Math.ceil(filtered.length / pageSize);
  const currentPage = Math.max(1, Math.min(page, pages));

  return {
    query,
    search,
    pages,
    currentPage,
    setPage,
    filteredCount: filtered.length,
    hasMultiplePortfolios: new Set(allPositions.map(({ portfolio }) => portfolio.id)).size > 1,
    positions: filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    selected: allPositions.find((position) => position.id === selectedId),
    select: onOpenPosition ?? ((position: Position) => setSelectedId(position.id)),
    close: () => setSelectedId(undefined),
    updateSearch(value: string) {
      setSearch(value);
      setPage(1);
    }
  };
}

export type PositionsModel = ReturnType<typeof usePositionsView>;
