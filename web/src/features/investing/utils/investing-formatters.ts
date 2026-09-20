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
  if (!totalBasis || !quantity) return '—';
  const basisNum = Number.parseFloat(String(totalBasis));
  const qtyNum = Number.parseFloat(String(quantity));
  if (Number.isNaN(basisNum) || Number.isNaN(qtyNum) || qtyNum === 0) {
    return '—';
  }
  const avg = basisNum / qtyNum;
  return formatMoney(avg.toFixed(4), currency);
}
