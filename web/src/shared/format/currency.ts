import { getDisplayNames, getNumberFormat } from './format';

export function getCurrencySymbol(currencyCode: string, locale: string): string {
  try {
    const parts = getNumberFormat(locale, {
      style: 'currency',
      currency: currencyCode,
      currencyDisplay: 'narrowSymbol'
    }).formatToParts(1);

    return parts.find((part) => part.type === 'currency')?.value ?? currencyCode;
  } catch {
    return currencyCode;
  }
}

export function getCurrencyInfo(currencyCode: string, language: string, locale: string) {
  let name = currencyCode;
  try {
    name = getDisplayNames(language, { type: 'currency' }).of(currencyCode) ?? currencyCode;
  } catch {
    // Non-ISO currency codes fall back to the raw code.
  }

  return {
    currency: currencyCode,
    name,
    symbol: getCurrencySymbol(currencyCode, locale)
  };
}
