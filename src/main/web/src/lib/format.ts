export const format = {
  toLocalePercentage: (value: number, decimalPlaces = 2, locale = 'tr-TR') => {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      throw new Error('Invalid input: value must be a number');
    }

    return (value / 100).toLocaleString(locale, {
      style: 'percent',
      minimumFractionDigits: decimalPlaces,
      notation: 'compact'
    });
  },
  toShortDate: (date: Date, locale = 'tr-TR') => {
    return date.toLocaleDateString(locale, {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  },
  toShortDateTime: (date: Date, locale = 'tr-TR') => {
    return date.toLocaleDateString(locale, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  },
  toFullDateTime: (date: Date, locale = 'tr-TR') => {
    return date.toLocaleDateString(locale, {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  },
  toCurrency: (value: number, compact = true, currency = 'TRY', locale = 'tr-TR', minimumFractionDigits = 2, maximumFractionDigits = 2) => {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currency,
      notation: compact ? 'compact' : 'standard',
      minimumFractionDigits: minimumFractionDigits,
      maximumFractionDigits: maximumFractionDigits
    }).format(value);
  },
  currency: (value: number, options?: Intl.NumberFormatOptions, locale = 'tr-TR') => {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: 'TRY',
      ...options
    }).format(value);
  },
  toHumanizedNumber: (value: number, locale = 'tr-TR') => {
    return new Intl.NumberFormat(locale, {
      notation: 'compact',
      compactDisplay: 'short'
    }).format(value);
  }
};
