import type { Position } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics } from '~/features/positions/position-presentation';

export function getPortfolioBalances(positions: Position[]) {
  const groups = new Map<string, Position[]>();
  for (const position of positions) {
    const group = groups.get(position.currencyCode) ?? [];
    group.push(position);
    groups.set(position.currencyCode, group);
  }

  return [...groups].map(([currency, positions]) => {
    const priced = positions
      .flatMap((position) => {
        const { value, profit, returnPercentage } = getPositionMetrics(position);
        return value != null ? [{ position, value, profit: profit ?? 0, returnPercentage }] : [];
      })
      .sort((a, b) => b.value - a.value);
    const total = priced.reduce((sum, { value }) => sum + value, 0);
    const profit = priced.reduce((sum, position) => sum + position.profit, 0);
    const quotedCost = priced.reduce((sum, { position }) => sum + position.quantity * position.avgCost, 0);
    return {
      currency,
      total,
      profit,
      returnPercentage: quotedCost > 0 ? (profit / quotedCost) * 100 : undefined,
      cost: positions.reduce((sum, position) => sum + position.total, 0),
      unquoted: positions.length - priced.length,
      hasQuotes: priced.length > 0,
      segments:
        total > 0
          ? priced
              .filter(({ value }) => value > 0)
              .map((segment, index) => ({
                ...segment,
                weight: (segment.value / total) * 100,
                colorIndex: index % 6
              }))
          : []
    };
  });
}

export type PortfolioBalance = ReturnType<typeof getPortfolioBalances>[number];
