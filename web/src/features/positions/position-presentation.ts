import type { components } from '~/api/schema';

export function getPositionCurrencyOptions(currency: string, _market?: boolean): Intl.NumberFormatOptions {
  return { currency };
}

export function getPositionMetrics({ instrument, quantity, avgCost }: components['schemas']['PositionInfo']) {
  const price = instrument.last ?? undefined;
  const value = price == null ? undefined : price * quantity;
  const profit = price == null ? undefined : (price - avgCost) * quantity;
  const returnPercentage = price != null && avgCost > 0 ? ((price - avgCost) / avgCost) * 100 : undefined;
  const dailyChange = instrument.dailyChange ?? undefined;
  const dailyProfit = dailyChange == null ? undefined : dailyChange * quantity;
  const previousClose = price != null && dailyChange != null ? price - dailyChange : undefined;
  const dailyChangePercentage =
    previousClose != null && previousClose > 0 && dailyChange != null ? (dailyChange / previousClose) * 100 : undefined;
  return { price, value, profit, returnPercentage, dailyProfit, dailyChangePercentage };
}

export interface PositionAllocation {
  weight: number;
  colorIndex: number;
}

export interface PositionHighlight {
  id?: string;
  onHover: (id?: string) => void;
  onFocus: (id?: string) => void;
}

export function getPositionHighlightProps(id: string, highlight?: PositionHighlight) {
  if (!highlight) return {};
  return {
    'data-highlighted': highlight.id === id || undefined,
    onMouseEnter: () => highlight.onHover(id),
    onMouseLeave: () => highlight.onHover(undefined),
    onFocus: () => highlight.onFocus(id),
    onBlur: () => highlight.onFocus(undefined)
  };
}

const DEFAULT_MAX_DATA_AGE_SECONDS = 15 * 60 * 60;

export function isDataStale(lastUpdatedTimestamp: Date | string | undefined) {
  if (!lastUpdatedTimestamp) return false;
  return Date.now() - new Date(lastUpdatedTimestamp).getTime() > DEFAULT_MAX_DATA_AGE_SECONDS * 1000;
}
