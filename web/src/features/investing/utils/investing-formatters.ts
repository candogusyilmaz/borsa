import { formatMoney } from '@/shared/format/money';
import type { CalculationPolicy, ProjectionStatus, TradeSide } from '../types';

export function formatQuantity(quantity: string | number | null | undefined): string {
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
  currency: string = 'USD'
): string {
  if (!totalBasis || !quantity) return '—';
  const basisNum = Number.parseFloat(String(totalBasis));
  const qtyNum = Number.parseFloat(String(quantity));
  if (Number.isNaN(basisNum) || Number.isNaN(qtyNum) || qtyNum === 0) {
    return '—';
  }
  const avg = basisNum / qtyNum;
  return formatMoney(avg.toFixed(4), currency);
}

export interface RealizedPnlPresentation {
  text: string;
  isProfit: boolean;
  isLoss: boolean;
  isZero: boolean;
  badgeColor: 'teal' | 'red' | 'gray';
  prefix: string;
}

export function getRealizedPnlPresentation(pnl: string | number | null | undefined, currency: string = 'USD'): RealizedPnlPresentation {
  if (pnl === null || pnl === undefined || pnl === '') {
    return {
      text: formatMoney('0', currency),
      isProfit: false,
      isLoss: false,
      isZero: true,
      badgeColor: 'gray',
      prefix: ''
    };
  }

  const num = typeof pnl === 'string' ? Number.parseFloat(pnl) : pnl;
  if (Number.isNaN(num) || num === 0) {
    return {
      text: formatMoney('0', currency),
      isProfit: false,
      isLoss: false,
      isZero: true,
      badgeColor: 'gray',
      prefix: ''
    };
  }

  if (num > 0) {
    return {
      text: `+${formatMoney(num, currency)} Profit`,
      isProfit: true,
      isLoss: false,
      isZero: false,
      badgeColor: 'teal',
      prefix: '+'
    };
  }

  return {
    text: `${formatMoney(num, currency)} Loss`,
    isProfit: false,
    isLoss: true,
    isZero: false,
    badgeColor: 'red',
    prefix: ''
  };
}

export function getTradeSideBadgeColor(side: TradeSide): 'teal' | 'indigo' {
  return side === 'BUY' ? 'teal' : 'indigo';
}

export function getTradeSideLabel(side: TradeSide): string {
  return side === 'BUY' ? 'Buy' : 'Sell';
}

export function getCalculationPolicyLabel(policy: CalculationPolicy): string {
  switch (policy) {
    case 'WEIGHTED_AVERAGE_ECONOMIC_V1':
      return 'Weighted Average Cost';
    default:
      return policy;
  }
}

export function getProjectionStatusLabel(status: ProjectionStatus): string {
  switch (status) {
    case 'CURRENT':
      return 'Up to date';
    case 'STALE':
      return 'Pending update';
    case 'REBUILDING':
      return 'Updating...';
    case 'FAILED':
      return 'Needs attention';
    default:
      return status;
  }
}

export function getProjectionStatusBadgeColor(status: ProjectionStatus): 'teal' | 'yellow' | 'blue' | 'red' | 'gray' {
  switch (status) {
    case 'CURRENT':
      return 'teal';
    case 'STALE':
      return 'yellow';
    case 'REBUILDING':
      return 'blue';
    case 'FAILED':
      return 'red';
    default:
      return 'gray';
  }
}
