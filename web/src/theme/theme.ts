import { createTheme } from '@mantine/core';
import { componentOverrides } from './components';
import { brand, neutral, semanticColors } from './palette';

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 6 },

  colors: {
    brand,
    neutral,
    ...semanticColors
  },

  fontFamily: 'Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',

  fontFamilyMonospace: '"SFMono-Regular", Consolas, "Liberation Mono", monospace',

  fontSizes: {
    xs: '0.75rem',
    sm: '0.875rem',
    md: '1rem',
    lg: '1.125rem',
    xl: '1.25rem'
  },

  lineHeights: {
    xs: '1.35',
    sm: '1.45',
    md: '1.55',
    lg: '1.5',
    xl: '1.4'
  },

  headings: {
    fontFamily: 'Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontWeight: '600',
    sizes: {
      h1: { fontSize: '2rem', lineHeight: '1.15', fontWeight: '600' },
      h2: { fontSize: '1.625rem', lineHeight: '1.2', fontWeight: '600' },
      h3: { fontSize: '1.375rem', lineHeight: '1.25', fontWeight: '600' },
      h4: { fontSize: '1.125rem', lineHeight: '1.35', fontWeight: '600' },
      h5: { fontSize: '1rem', lineHeight: '1.4', fontWeight: '500' },
      h6: { fontSize: '0.875rem', lineHeight: '1.4', fontWeight: '500' }
    }
  },

  spacing: {
    xs: '0.5rem',
    sm: '0.75rem',
    md: '1rem',
    lg: '1.25rem',
    xl: '1.5rem'
  },

  radius: {
    xs: '0.375rem',
    sm: '0.5rem',
    md: '0.75rem',
    lg: '1rem',
    xl: '1.375rem'
  },

  defaultRadius: 'md',

  shadows: {
    xs: '0 1px 2px rgba(15, 23, 42, 0.04)',
    sm: '0 2px 8px rgba(15, 23, 42, 0.06)',
    md: '0 6px 20px rgba(15, 23, 42, 0.08)',
    lg: '0 12px 32px rgba(15, 23, 42, 0.10)',
    xl: '0 18px 48px rgba(15, 23, 42, 0.12)'
  },

  breakpoints: {
    xs: '30em',
    sm: '48em',
    md: '64em',
    lg: '75em',
    xl: '90em'
  },

  focusRing: 'auto',
  cursorType: 'pointer',

  components: componentOverrides
});
