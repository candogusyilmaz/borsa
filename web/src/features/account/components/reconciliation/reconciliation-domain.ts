import { toDateTimeLocal } from '@/shared/format/date-time';
import type { ReconciliationLifecycleStatus, ReconciliationResolution } from './reconciliation-types';

/**
 * Clock safety buffer (1 second) to prevent backend FUTURE_TIME_NOT_ALLOWED
 * errors when statement period presets default to the current instant.
 */
export const RECONCILIATION_CLOCK_SAFETY_MS = 1_000;

export function getResolutionBadgeColor(resolution: ReconciliationResolution): string {
  switch (resolution) {
    case 'BALANCED':
      return 'teal';
    case 'ADJUSTED':
      return 'orange';
    default:
      return 'gray';
  }
}

export function getResolutionLabel(resolution: ReconciliationResolution): string {
  switch (resolution) {
    case 'BALANCED':
      return 'Balanced';
    case 'ADJUSTED':
      return 'Adjusted';
    default:
      return resolution;
  }
}

export function getLifecycleStatusBadgeColor(status: ReconciliationLifecycleStatus): string {
  switch (status) {
    case 'CURRENT':
      return 'teal';
    case 'STALE':
      return 'orange';
    case 'SUPERSEDED':
      return 'gray';
    default:
      return 'gray';
  }
}

export function getLifecycleStatusLabel(status: ReconciliationLifecycleStatus): string {
  switch (status) {
    case 'CURRENT':
      return 'Current';
    case 'STALE':
      return 'Stale';
    case 'SUPERSEDED':
      return 'Superseded';
    default:
      return status;
  }
}

export function getLifecycleStatusDescription(status: ReconciliationLifecycleStatus): string {
  switch (status) {
    case 'CURRENT':
      return 'This reconciliation accurately represents the current ledger state for its period.';
    case 'STALE':
      return 'New postings with effective dates within this statement period were added after this reconciliation was committed.';
    case 'SUPERSEDED':
      return 'This reconciliation has been superseded by a subsequent correction entry.';
    default:
      return '';
  }
}

export function isZeroAmount(amountStr?: string | null): boolean {
  if (!amountStr) return true;
  const num = Number.parseFloat(amountStr);
  return Number.isNaN(num) || Math.abs(num) < 0.000001;
}

export function toDateTimeLocalString(date: Date): string {
  return toDateTimeLocal(date);
}

export function getPreviousMonthPeriod(): { opening: string; closing: string } {
  const now = new Date();
  const startOfPrevMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1, 0, 0, 0);
  const endOfPrevMonth = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59);
  return {
    opening: toDateTimeLocal(startOfPrevMonth),
    closing: toDateTimeLocal(endOfPrevMonth)
  };
}

export function getCurrentMonthPeriod(): { opening: string; closing: string } {
  const now = new Date(Date.now() - RECONCILIATION_CLOCK_SAFETY_MS);
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0);
  return {
    opening: toDateTimeLocal(startOfMonth),
    closing: toDateTimeLocal(now)
  };
}

export function getLast30DaysPeriod(): { opening: string; closing: string } {
  const now = new Date(Date.now() - RECONCILIATION_CLOCK_SAFETY_MS);
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  return {
    opening: toDateTimeLocal(thirtyDaysAgo),
    closing: toDateTimeLocal(now)
  };
}
