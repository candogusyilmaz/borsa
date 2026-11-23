import { ActionIcon, Button, createTheme, defaultVariantColorsResolver, Input, NumberInput } from '@mantine/core';

const fontFamily = '"Inter", -apple-system, BlinkMacSystemFont, system-ui, sans-serif';

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 8, dark: 0 },
  white: '#FAF9F6',
  black: '#0D0D0F',
  colors: {
    brand: ['#F4F4F5', '#EAE8E1', '#DAD7CD', '#CFCBC0', '#A39E91', '#777369', '#57544D', '#3A3934', '#242320', '#1C1B18'],
    gray: ['#FAF9F6', '#F3F2EE', '#EBE9E2', '#E2DFD7', '#CFCBC0', '#9A968C', '#6E6B64', '#53504A', '#36342F', '#1C1B18'],
    dark: ['#F4F4F5', '#C5C5CB', '#8E8E98', '#63636E', '#34343D', '#222228', '#131316', '#0D0D0F', '#0A0A0C', '#070709'],
    profit: ['#ECFDF5', '#D1FAE5', '#A7F3D0', '#6EE7B7', '#34D399', '#22C55E', '#16A34A', '#15803D', '#166534', '#14532D'],
    loss: ['#FFF1F2', '#FFE4E6', '#FECDD3', '#FDA4AF', '#FB7185', '#F43F5E', '#BE123C', '#9F1239', '#881337', '#4C0519']
  },
  variantColorResolver: (input) => {
    const colors = defaultVariantColorsResolver(input);
    if (input.variant !== 'filled' || (input.color ?? input.theme.primaryColor) !== 'brand') return colors;
    return { ...colors, color: 'var(--app-on-primary)' };
  },
  fontFamily,
  fontFamilyMonospace: '"JetBrains Mono", ui-monospace, monospace',
  headings: { fontFamily, fontWeight: '600', textWrap: 'balance' },
  fontWeights: { regular: '400', medium: '500', bold: '600' },
  defaultRadius: 'sm',
  shadows: {
    xs: '0 1px 2px rgb(0 0 0 / 0.04)',
    sm: '0 2px 6px rgb(0 0 0 / 0.06)',
    md: '0 4px 12px rgb(0 0 0 / 0.08)',
    lg: '0 8px 20px rgb(0 0 0 / 0.10)',
    xl: '0 12px 32px rgb(0 0 0 / 0.12)'
  },
  cursorType: 'pointer',
  components: {
    Button: Button.extend({ defaultProps: { size: 'sm' } }),
    ActionIcon: ActionIcon.extend({ defaultProps: { size: 36, variant: 'subtle' } }),
    Input: Input.extend({ defaultProps: { size: 'sm' } }),
    InputWrapper: Input.Wrapper.extend({ defaultProps: { inputWrapperOrder: ['label', 'input', 'description', 'error'] } }),
    NumberInput: NumberInput.extend({ defaultProps: { inputMode: 'decimal' } })
  }
});
