export type FinanceTone = 'profit' | 'loss';

export function getFinanceTone(value?: number | null): FinanceTone | undefined {
  if (value == null || !Number.isFinite(value)) return undefined;
  if (value > 0) return 'profit';
  if (value < 0) return 'loss';
  return undefined;
}
