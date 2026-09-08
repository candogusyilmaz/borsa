export function selectBySign<T>(
  value: number | null | undefined,
  options: {
    null?: T | ((v: null | undefined) => T);
    zero?: T | ((v: number) => T);
    positive?: T | ((v: number) => T);
    negative?: T | ((v: number) => T);
  }
): T | undefined {
  if (value === null || value === undefined) {
    const result = options.null;
    return typeof result === 'function' ? (result as (v: null | undefined) => T)(value) : result;
  }
  if (value === 0) {
    const result = options.zero;
    return typeof result === 'function' ? (result as (v: number) => T)(value) : result;
  }
  if (value > 0) {
    const result = options.positive;
    return typeof result === 'function' ? (result as (v: number) => T)(value) : result;
  }
  if (value < 0) {
    const result = options.negative;
    return typeof result === 'function' ? (result as (v: number) => T)(value) : result;
  }
}

// biome-ignore lint/suspicious/noExplicitAny: int
export function determinate(value: any, returns: { naEq?: any; gt?: any; lt?: any }) {
  if (!value || value === 0) return returns.naEq;

  if (value > 0) return returns.gt;

  if (value < 0) return returns.lt;
}

// biome-ignore lint/suspicious/noExplicitAny: int
export function determinateFn(value: any, returns: { naEq?: (value) => any; gt?: (value) => any; lt?: (value) => any }) {
  if (!value || value === 0) return returns.naEq?.(value);

  if (value > 0) return returns.gt?.(value);

  if (value < 0) return returns.lt?.(value);
}

interface GetColorByReturnPercentageOptionsType {
  lastUpdatedTimestamp?: Date | string | undefined;
}
export function getColorByReturnPercentage(value: number, options: GetColorByReturnPercentageOptionsType = {}) {
  const { lastUpdatedTimestamp } = options;

  if (lastUpdatedTimestamp && isDataStale(lastUpdatedTimestamp)) {
    return '#899499';
  }

  return selectBySign<string>(value, {
    zero: '#7F8C8D',
    positive: 'teal',
    negative: 'red.6'
  });
}

const DEFAULT_MAX_DATA_AGE_SECONDS = 15 * 60 * 60;
export function isDataStale(lastUpdatedTimestamp: Date | string | undefined) {
  const now = Date.now();

  if (lastUpdatedTimestamp && new Date(lastUpdatedTimestamp).getTime() <= now) {
    const dataAgeMs = now - new Date(lastUpdatedTimestamp).getTime();
    const dataAgeSeconds = dataAgeMs / 1000;

    if (dataAgeSeconds > DEFAULT_MAX_DATA_AGE_SECONDS) {
      return true;
    }
  }
  return false;
}
