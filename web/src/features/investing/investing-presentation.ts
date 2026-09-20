import { toFinancialDecimal } from '@/shared/finance/decimal';
import { formatMoney } from '@/shared/format/money';
import type { CalculationPolicy, ProjectionStatus, TradeSide } from './types';

export interface RealizedPnlPresentation {
  text: string;
  isProfit: boolean;
  isLoss: boolean;
  isZero: boolean;
  badgeColor: 'teal' | 'red' | 'gray';
  prefix: string;
}

export function getRealizedPnlPresentation(pnl: string | number | null | undefined, currency = 'USD') {
  if (pnl === null || pnl === undefined || pnl === '') {
    return {
      text: formatMoney('0', currency),
      isProfit: false,
      isLoss: false,
      isZero: true,
      badgeColor: 'gray' as const,
      prefix: ''
    };
  }

  const dec = toFinancialDecimal(pnl);
  if (!dec || dec.isZero()) {
    return {
      text: formatMoney('0', currency),
      isProfit: false,
      isLoss: false,
      isZero: true,
      badgeColor: 'gray' as const,
      prefix: ''
    };
  }

  if (dec.isPositive()) {
    return {
      text: `+${formatMoney(pnl, currency)} Profit`,
      isProfit: true,
      isLoss: false,
      isZero: false,
      badgeColor: 'teal' as const,
      prefix: '+'
    };
  }

  return {
    text: `${formatMoney(pnl, currency)} Loss`,
    isProfit: false,
    isLoss: true,
    isZero: false,
    badgeColor: 'red' as const,
    prefix: ''
  };
}

export function getTradeSideBadgeColor(side: TradeSide) {
  return side === 'BUY' ? 'teal' : 'indigo';
}

export function getTradeSideLabel(side: TradeSide) {
  return side === 'BUY' ? 'Buy' : 'Sell';
}

export function getCalculationPolicyLabel(policy: CalculationPolicy) {
  switch (policy) {
    case 'WEIGHTED_AVERAGE_ECONOMIC_V1':
      return 'Weighted Average Cost';
    default:
      return policy;
  }
}

export function getProjectionStatusLabel(status: ProjectionStatus) {
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

export function getProjectionStatusBadgeColor(status: ProjectionStatus) {
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
