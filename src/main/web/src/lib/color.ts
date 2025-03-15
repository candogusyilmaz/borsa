export const getColorBySign = (
  value: number,
  opt = {
    negative: 'red',
    zero: 'dimmed',
    positive: 'teal'
  }
) => {
  if (value === 0) return opt.zero;
  if (value > 0) return opt.positive;
  return opt.negative;
};
