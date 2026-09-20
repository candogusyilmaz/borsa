export function formatMoney(amount: string | number | null | undefined, currency: string = 'USD', locale: string = 'en-US') {
  if (amount === null || amount === undefined || amount === '') {
    return '—';
  }

  const numeric = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (Number.isNaN(numeric)) {
    return String(amount);
  }

  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(numeric);
  } catch {
    return `${numeric.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
  }
}
