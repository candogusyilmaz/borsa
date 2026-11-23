import type { components } from '~/api/schema';

export function getPositionMetrics({ instrument, quantity, avgCost }: components['schemas']['PositionInfo']) {
  const price = instrument.last ?? undefined;
  const value = price == null ? undefined : price * quantity;
  const profit = price == null ? undefined : (price - avgCost) * quantity;
  const returnPercentage = price != null && avgCost > 0 ? ((price - avgCost) / avgCost) * 100 : undefined;
  const dailyProfit = instrument.dailyChange == null ? undefined : instrument.dailyChange * quantity;
  return { price, value, profit, returnPercentage, dailyProfit };
}

const DEFAULT_MAX_DATA_AGE_SECONDS = 15 * 60 * 60;

export function isDataStale(lastUpdatedTimestamp: Date | string | undefined) {
  if (!lastUpdatedTimestamp) return false;
  return Date.now() - new Date(lastUpdatedTimestamp).getTime() > DEFAULT_MAX_DATA_AGE_SECONDS * 1000;
}
