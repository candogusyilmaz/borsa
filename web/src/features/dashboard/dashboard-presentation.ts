export function tone(value?: number) {
  if (value != null && value > 0) return 'profit';
  if (value != null && value < 0) return 'loss';
}
