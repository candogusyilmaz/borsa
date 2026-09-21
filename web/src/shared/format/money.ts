import { FinancialDecimal, toFinancialDecimal } from '@/shared/finance/decimal';

const DEFAULT_LOCALE = 'en-US';
const DEFAULT_CURRENCY = 'USD';
const DEFAULT_MIN_FRACTION_DIGITS = 2;
const DEFAULT_MAX_FRACTION_DIGITS = 2;
const DEFAULT_MAX_ADAPTIVE_FRACTION_DIGITS = 18;

export interface FormatMoneyOptions {
  locale?: string;
  minimumFractionDigits?: number;
  maximumFractionDigits?: number;
  adaptivePrecision?: boolean;
  maxAdaptiveFractionDigits?: number;
}

/**
 * Isolated formatting helper to format arbitrary-precision numeric strings
 * via Intl.NumberFormat without precision-lossy Number coercion.
 */
function formatIntl(formatter: Intl.NumberFormat, value: string | number) {
  return formatter.format(value as unknown as number);
}

function formatRawCurrency(value: string | number, currency: string, locale: string, minDigits: number, maxDigits: number) {
  try {
    const formatter = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: minDigits,
      maximumFractionDigits: maxDigits
    });
    return formatIntl(formatter, value);
  } catch {
    try {
      const numberFormatter = new Intl.NumberFormat(locale, {
        minimumFractionDigits: minDigits,
        maximumFractionDigits: maxDigits
      });
      return `${formatIntl(numberFormatter, value)} ${currency}`;
    } catch {
      return `${value} ${currency}`;
    }
  }
}

export function formatMoney(
  amount: string | number | FinancialDecimal | null | undefined,
  currency: string = DEFAULT_CURRENCY,
  options?: FormatMoneyOptions
) {
  if (amount === null || amount === undefined || (typeof amount === 'string' && amount.trim() === '')) {
    return '—';
  }

  if (typeof amount !== 'string' && typeof amount !== 'number' && !(amount instanceof FinancialDecimal)) {
    return '—';
  }

  const dec = toFinancialDecimal(amount);
  if (!dec) {
    return String(amount);
  }

  const activeCurrency = currency || DEFAULT_CURRENCY;
  const locale = options?.locale ?? DEFAULT_LOCALE;
  const minDigits = options?.minimumFractionDigits ?? DEFAULT_MIN_FRACTION_DIGITS;
  let maxDigits = options?.maximumFractionDigits ?? DEFAULT_MAX_FRACTION_DIGITS;

  if (options?.adaptivePrecision && !dec.isZero()) {
    const roundsToZero = new FinancialDecimal(dec.toFixed(maxDigits)).isZero();
    if (roundsToZero) {
      const maxAdaptive = options.maxAdaptiveFractionDigits ?? DEFAULT_MAX_ADAPTIVE_FRACTION_DIGITS;
      if (new FinancialDecimal(dec.toFixed(maxAdaptive)).isZero()) {
        const smallestUnit = `0.${'0'.repeat(maxAdaptive - 1)}1`;
        const formattedThreshold = formatRawCurrency(smallestUnit, activeCurrency, locale, maxAdaptive, maxAdaptive);
        return dec.isPositive() ? `< ${formattedThreshold}` : `> -${formattedThreshold}`;
      }
      maxDigits = Math.max(maxDigits, maxAdaptive);
    }
  }

  return formatRawCurrency(dec.toString(), activeCurrency, locale, minDigits, maxDigits);
}
