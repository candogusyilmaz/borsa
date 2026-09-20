const DECIMAL_PATTERN = /^-?(?:0|[1-9]\d*)(?:\.\d+)?$/;
const UNSIGNED_DECIMAL_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d+)?$/;
const ZERO_DECIMAL_PATTERN = /^0+(?:\.0+)?$/;

export function isDecimal(value: string | null | undefined) {
  if (typeof value !== 'string') return false;
  const trimmed = value.trim();
  if (!trimmed) return false;
  return DECIMAL_PATTERN.test(trimmed);
}

export function isNonNegativeDecimal(value: string | null | undefined) {
  if (typeof value !== 'string') return false;
  const trimmed = value.trim();
  if (!trimmed) return false;
  return UNSIGNED_DECIMAL_PATTERN.test(trimmed);
}

export function isPositiveDecimal(value: string | null | undefined) {
  if (typeof value !== 'string') return false;
  const trimmed = value.trim();
  if (!trimmed) return false;
  return UNSIGNED_DECIMAL_PATTERN.test(trimmed) && !ZERO_DECIMAL_PATTERN.test(trimmed);
}
