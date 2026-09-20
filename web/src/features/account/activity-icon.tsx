import {
  ArrowCounterClockwiseIcon,
  ArrowDownLeftIcon,
  ArrowsLeftRightIcon,
  ArrowUpRightIcon,
  BankIcon,
  ReceiptIcon,
  SlidersIcon,
  TrendDownIcon,
  TrendUpIcon
} from '@phosphor-icons/react';
import type { ActivityType } from './types';

interface ActivityTypeIconProps {
  type: ActivityType;
  size?: number;
  className?: string;
}

export function ActivityTypeIcon({ type, size = 18, className }: ActivityTypeIconProps) {
  switch (type) {
    case 'CASH_DEPOSIT':
      return <ArrowDownLeftIcon size={size} weight="bold" color="var(--mantine-color-teal-6)" className={className} />;
    case 'CASH_WITHDRAWAL':
      return <ArrowUpRightIcon size={size} weight="bold" color="var(--mantine-color-orange-6)" className={className} />;
    case 'CASH_FEE':
      return <ReceiptIcon size={size} weight="bold" color="var(--mantine-color-red-6)" className={className} />;
    case 'CASH_INTEREST_CREDIT':
      return <TrendUpIcon size={size} weight="bold" color="var(--mantine-color-cyan-6)" className={className} />;
    case 'OWNED_TRANSFER':
      return <ArrowsLeftRightIcon size={size} weight="bold" color="var(--mantine-color-blue-6)" className={className} />;
    case 'OPENING_BALANCE':
      return <BankIcon size={size} weight="duotone" color="var(--mantine-color-indigo-6)" className={className} />;
    case 'REVERSAL':
      return <ArrowCounterClockwiseIcon size={size} weight="bold" color="var(--mantine-color-violet-6)" className={className} />;
    case 'RECONCILIATION_ADJUSTMENT':
      return <SlidersIcon size={size} weight="bold" color="var(--mantine-color-cyan-6)" className={className} />;
    case 'SECURITY_BUY':
      return <TrendUpIcon size={size} weight="bold" color="var(--mantine-color-teal-6)" className={className} />;
    case 'SECURITY_SELL':
      return <TrendDownIcon size={size} weight="bold" color="var(--mantine-color-indigo-6)" className={className} />;
    default:
      return <ReceiptIcon size={size} weight="duotone" className={className} />;
  }
}
