const VALID_DECIMAL_PATTERN = /^[+-]?(?:0|[1-9]\d*)(?:\.\d+)?$/;

export interface FormatMoneyOptions {
  minimumFractionDigits?: number;
  maximumFractionDigits?: number;
}

export function formatMoney(
  amount: string | number | null | undefined,
  currency: string = 'USD',
  locale: string = 'en-US',
  options?: FormatMoneyOptions
) {
  if (amount === null || amount === undefined || amount === '') {
    return '—';
  }

  let formattedValue: string | number;
  if (typeof amount === 'string') {
    const trimmed = amount.trim();
    if (!VALID_DECIMAL_PATTERN.test(trimmed)) {
      return String(amount);
    }
    formattedValue = trimmed;
  } else if (typeof amount === 'number') {
    if (!Number.isFinite(amount)) {
      return String(amount);
    }
    formattedValue = amount;
  } else {
    return '—';
  }

  const minDigits = options?.minimumFractionDigits ?? 2;
  const maxDigits = options?.maximumFractionDigits ?? 2;

  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: minDigits,
      maximumFractionDigits: maxDigits
    }).format(formattedValue as unknown as number);
  } catch {
    try {
      const formattedNumber = new Intl.NumberFormat(locale, {
        minimumFractionDigits: minDigits,
        maximumFractionDigits: maxDigits
      }).format(formattedValue as unknown as number);
      return `${formattedNumber} ${currency}`;
    } catch {
      return `${formattedValue} ${currency}`;
    }
  }
}
