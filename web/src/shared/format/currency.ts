export function getCurrencySymbol(currencyCode: string, locale: string) {
  try {
    const parts = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currencyCode,
      currencyDisplay: 'narrowSymbol'
    }).formatToParts(1);

    return parts.find((part) => part.type === 'currency')?.value ?? currencyCode;
  } catch {
    return currencyCode;
  }
}
