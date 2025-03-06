export function getCurrencySymbol(currencyCode: string) {
  try {
    // Create a number format with the specified currency
    const formatter = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currencyCode,
      currencyDisplay: 'narrowSymbol'
    });

    // Extract the currency symbol from the formatted string
    const parts = formatter.formatToParts(1);

    const currencyPart = parts.find((part) => part.type === 'currency');

    return currencyPart ? currencyPart.value : currencyCode;
  } catch (error) {
    console.error(`Error getting symbol for ${currencyCode}:`, error);
    return currencyCode;
  }
}
