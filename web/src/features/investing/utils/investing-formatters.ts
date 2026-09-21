import { toFinancialDecimal } from '@/shared/finance/decimal';
import { formatMoney } from '@/shared/format/money';

export function formatQuantity(quantity: string | number | null | undefined) {
  if (quantity === null || quantity === undefined || quantity === '') {
    return '0';
  }
  const str = String(quantity);
  // If scientific notation or invalid
  if (Number.isNaN(Number(str))) {
    return str;
  }
  // Trim trailing zeros after decimal point if whole number, but preserve fractional precision
  if (str.includes('.')) {
    const trimmed = str.replace(/0+$/, '').replace(/\.$/, '');
    return trimmed.length > 0 ? trimmed : '0';
  }
  return str;
}

export function formatUnitCost(
  totalBasis: string | number | null | undefined,
  quantity: string | number | null | undefined,
  currency = 'USD'
) {
  if (totalBasis === null || totalBasis === undefined || totalBasis === '') return '—';
  if (quantity === null || quantity === undefined || quantity === '') return '—';

  const basisDec = toFinancialDecimal(totalBasis);
  const qtyDec = toFinancialDecimal(quantity);
  if (!basisDec || !qtyDec || qtyDec.isZero()) {
    return '—';
  }

  const avg = basisDec.dividedBy(qtyDec);
  return formatMoney(avg.toFixed(4), currency, {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4
  });
}
