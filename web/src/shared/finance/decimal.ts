import Decimal, { type Numeric } from 'decimal.js-light';

export const FinancialDecimal = Decimal.clone({
  precision: 50,
  toExpNeg: -50,
  toExpPos: 50
});

export type FinancialDecimal = InstanceType<typeof FinancialDecimal>;

export function toFinancialDecimal(value: Numeric | null | undefined): FinancialDecimal | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  try {
    return new FinancialDecimal(value);
  } catch {
    return null;
  }
}
