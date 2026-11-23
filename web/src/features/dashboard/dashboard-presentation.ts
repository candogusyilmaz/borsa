const signedPercentNumber = new Intl.NumberFormat('tr-TR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero'
});

export function tone(value?: number) {
  if (value != null && value > 0) return 'profit';
  if (value != null && value < 0) return 'loss';
}

export function signedPercentage(value: number) {
  return `${signedPercentNumber.format(value)}%`;
}
