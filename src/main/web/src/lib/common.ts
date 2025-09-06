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
