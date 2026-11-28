import dayjs from 'dayjs';

const numberFormats = new Map<string, Intl.NumberFormat>();
const dateFormats = new Map<string, Intl.DateTimeFormat>();
const collators = new Map<string, Intl.Collator>();
const displayNames = new Map<string, Intl.DisplayNames>();
const separatorsCache = new Map<
  string,
  { thousandSeparator: string | false; decimalSeparator: string; allowedDecimalSeparators: string[] }
>();

function cached<T>(map: Map<string, T>, key: string, create: () => T): T {
  let value = map.get(key);
  if (!value) {
    value = create();
    map.set(key, value);
  }
  return value;
}

export function getNumberFormat(locale: string, options?: Intl.NumberFormatOptions): Intl.NumberFormat {
  const key = options ? `${locale}|${JSON.stringify(options)}` : locale;
  return cached(numberFormats, key, () => new Intl.NumberFormat(locale, options));
}

export function getDateTimeFormat(locale: string, options?: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = options ? `${locale}|${JSON.stringify(options)}` : locale;
  return cached(dateFormats, key, () => new Intl.DateTimeFormat(locale, options));
}

export function getCollator(locale: string, options?: Intl.CollatorOptions): Intl.Collator {
  const key = options ? `${locale}|${JSON.stringify(options)}` : locale;
  return cached(collators, key, () => new Intl.Collator(locale, options));
}

export function getDisplayNames(locale: string, options: Intl.DisplayNamesOptions): Intl.DisplayNames {
  return cached(displayNames, `${locale}|${JSON.stringify(options)}`, () => new Intl.DisplayNames([locale], options));
}

export function getNumberSeparators(locale: string) {
  return cached(separatorsCache, locale, () => {
    const parts = getNumberFormat(locale).formatToParts(1234.5);
    const decimalSeparator = parts.find(({ type }) => type === 'decimal')?.value ?? '.';
    return {
      thousandSeparator: parts.find(({ type }) => type === 'group')?.value ?? false,
      decimalSeparator,
      allowedDecimalSeparators: [decimalSeparator]
    };
  });
}

export function parseDateTimeInput(value: string | Date | null | undefined): Date | null {
  if (value == null) return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = dayjs(trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T'));
  return parsed.isValid() ? parsed.toDate() : null;
}

export function createFormat(locale: string) {
  function date(value: Date | number, options: Intl.DateTimeFormatOptions) {
    return getDateTimeFormat(locale, options).format(value);
  }

  function dateRange(start: Date | number, end: Date | number, options: Intl.DateTimeFormatOptions) {
    const formatter = getDateTimeFormat(locale, options) as Intl.DateTimeFormat & {
      formatRange(startDate: Date | number, endDate: Date | number): string;
    };
    return formatter.formatRange(start, end);
  }

  function number(value: number, options?: Intl.NumberFormatOptions) {
    return getNumberFormat(locale, options).format(value);
  }

  function currency(value: number, options?: Intl.NumberFormatOptions) {
    const code = options?.currency ?? 'TRY';
    const currencyDisplay = options?.currencyDisplay ?? (code.toUpperCase() === 'TRY' ? 'narrowSymbol' : undefined);
    try {
      return number(value, { style: 'currency', ...options, currency: code, currencyDisplay });
    } catch {
      const { currency: _currency, currencyDisplay: _currencyDisplay, style: _style, ...fallbackOptions } = options ?? {};
      return `${number(value, { style: 'decimal', ...fallbackOptions })} ${code}`;
    }
  }

  return {
    date,
    dateRange,
    number,
    toLocalePercentage: (value: number, decimalPlaces = 2, options?: Intl.NumberFormatOptions) =>
      number(value / 100, {
        style: 'percent',
        minimumFractionDigits: decimalPlaces,
        maximumFractionDigits: decimalPlaces,
        ...options
      }),
    toShortDate: (value: Date) => date(value, { day: '2-digit', month: 'short', year: 'numeric' }),
    toShortDateTime: (value: Date) =>
      date(value, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    toFullDateTime: (value: Date) => date(value, { day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' }),
    toCurrency: (value: number, compact = true, currencyCode = 'TRY', minimumFractionDigits = 2, maximumFractionDigits = 2) =>
      currency(value, {
        currency: currencyCode,
        notation: compact ? 'compact' : 'standard',
        minimumFractionDigits,
        maximumFractionDigits
      }),
    currency
  };
}
