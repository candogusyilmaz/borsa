import type { Position } from './hooks/use-positions-view';
import { getPositionMetrics } from './position-presentation';

export type PositionOrder = 'value' | 'symbol' | 'profit' | 'return-high' | 'return-low';

export interface PositionBalance {
  currency: string;
  value: number;
  cost: number;
  profit: number;
  quoted: number;
  missing: number;
}

function searchable(value: string) {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/ı/g, 'i');
}

function compareNumbers(a: number | undefined, b: number | undefined, direction: number) {
  if (a == null) return b == null ? 0 : 1;
  if (b == null) return -1;
  return (a - b) * direction;
}

export function getPositionsPageData(
  positions: Position[],
  search: string,
  portfolioId: string | null,
  order: PositionOrder,
  locale: string
) {
  const portfolios = new Map(positions.map(({ portfolio }) => [portfolio.id, portfolio]));
  const term = searchable(search.trim());
  const visible = positions
    .filter(
      ({ instrument, portfolio }) =>
        (!portfolioId || portfolio.id === portfolioId) &&
        (!term || [instrument.symbol, instrument.name, portfolio.name].some((item) => searchable(item).includes(term)))
    )
    .map((position) => ({ position, ...getPositionMetrics(position) }));

  const balances = new Map<string, PositionBalance>();
  for (const { position, value, profit } of visible) {
    const currency = position.currencyCode;
    const balance = balances.get(currency) ?? { currency, value: 0, cost: 0, profit: 0, quoted: 0, missing: 0 };
    if (value != null && profit != null) {
      balance.value += value;
      balance.cost += position.quantity * position.avgCost;
      balance.profit += profit;
      balance.quoted++;
    } else {
      balance.missing++;
    }
    balances.set(currency, balance);
  }

  const collator = new Intl.Collator(locale);
  visible.sort((a, b) => {
    let byMetric = 0;
    switch (order) {
      case 'value':
      case 'profit':
        byMetric = collator.compare(a.position.currencyCode, b.position.currencyCode) || compareNumbers(a[order], b[order], -1);
        break;
      case 'return-high':
      case 'return-low':
        byMetric = compareNumbers(a.returnPercentage, b.returnPercentage, order === 'return-high' ? -1 : 1);
        break;
    }
    return (
      byMetric ||
      collator.compare(a.position.instrument.symbol, b.position.instrument.symbol) ||
      collator.compare(a.position.portfolio.name, b.position.portfolio.name)
    );
  });

  const weights = new Map<string, number>();
  for (const { position, value } of visible) {
    const total = balances.get(position.currencyCode)?.value;
    if (value != null && total != null && total > 0) weights.set(position.id, (value / total) * 100);
  }

  return {
    portfolios: [...portfolios.values()],
    positions: visible.map(({ position }) => position),
    weights,
    balances: [...balances.values()].sort((a, b) => collator.compare(a.currency, b.currency))
  };
}
