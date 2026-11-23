export function createFormat(locale: string) {
  function date(value: Date | number, options: Intl.DateTimeFormatOptions) {
    return new Intl.DateTimeFormat(locale, options).format(value);
  }

  function number(value: number, options?: Intl.NumberFormatOptions) {
    return new Intl.NumberFormat(locale, options).format(value);
  }

  return {
    date,
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
    toCurrency: (value: number, compact = true, currency = 'TRY', minimumFractionDigits = 2, maximumFractionDigits = 2) =>
      number(value, {
        style: 'currency',
        currency,
        notation: compact ? 'compact' : 'standard',
        minimumFractionDigits,
        maximumFractionDigits
      }),
    currency: (value: number, options?: Intl.NumberFormatOptions) => number(value, { style: 'currency', currency: 'TRY', ...options })
  };
}

export function getNumberSeparators(locale: string) {
  const parts = new Intl.NumberFormat(locale).formatToParts(1234.5);
  return {
    thousandSeparator: parts.find(({ type }) => type === 'group')?.value ?? false,
    decimalSeparator: parts.find(({ type }) => type === 'decimal')?.value ?? '.'
  };
}
