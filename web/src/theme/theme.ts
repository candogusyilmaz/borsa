import { ActionIcon, Button, createTheme, defaultVariantColorsResolver, Input, NumberInput } from '@mantine/core';
import { MOBILE_BREAKPOINT, THEME_PALETTE } from './tokens';

const fontFamily = '"Inter", -apple-system, BlinkMacSystemFont, system-ui, sans-serif';
const { light, dark } = THEME_PALETTE;

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 8, dark: 0 },
  white: light.surface,
  black: dark.canvas,
  colors: {
    brand: [dark.text, light.raised, '#DAD7CD', light.strongBorder, '#A39E91', '#777369', '#57544D', '#3A3934', light.primary, light.text],
    gray: [
      light.surface,
      light.canvas,
      '#EBE9E2',
      light.border,
      light.strongBorder,
      light.placeholder,
      light.dimmed,
      '#53504A',
      '#36342F',
      light.text
    ],
    dark: [
      dark.text,
      '#C5C5CB',
      dark.dimmed,
      dark.placeholder,
      dark.strongBorder,
      dark.border,
      dark.surface,
      dark.canvas,
      '#0A0A0C',
      '#070709'
    ],
    profit: ['#ECFDF5', '#D1FAE5', '#A7F3D0', '#6EE7B7', dark.profit, '#22C55E', '#16A34A', light.profit, '#166534', '#14532D'],
    loss: ['#FFF1F2', '#FFE4E6', '#FECDD3', '#FDA4AF', dark.loss, '#F43F5E', light.loss, '#9F1239', '#881337', '#4C0519']
  },
  breakpoints: { sm: MOBILE_BREAKPOINT },
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
