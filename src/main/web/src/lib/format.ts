export const format = {
  toLocalePercentage: (value: number, decimalPlaces = 2, locale = 'tr-TR') => {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      throw new Error('Invalid input: value must be a number');
    }

    return value.toLocaleString(locale, {
      style: 'percent',
      minimumFractionDigits: decimalPlaces
    });
  }
};
