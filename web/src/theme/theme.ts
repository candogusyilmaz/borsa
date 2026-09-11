import { ActionIcon, Button, Card, createTheme, Input, type MantineColorsTuple, Paper } from '@mantine/core';

const brand: MantineColorsTuple = [
  '#F3F2FF',
  '#E7E5FF',
  '#D0CDFF',
  '#B5B1FF',
  '#9690FF',
  '#776FF8',
  '#625BF6',
  '#5149E0',
  '#413AB9',
  '#342F91'
];

const gray: MantineColorsTuple = [
  '#FAFBFC',
  '#F6F7F9',
  '#F0F2F5',
  '#E8EAF0',
  '#DCE0E7',
  '#9AA4B2',
  '#697586',
  '#525C6A',
  '#303741',
  '#171B22'
];

/**
 * This is intentionally blue-neutral rather than a pure gray/black scale.
 *
 * Mantine dark-mode defaults use roughly:
 *
 * dark.0 -> text
 * dark.2 -> dimmed
 * dark.3 -> placeholder
 * dark.4 -> borders
 * dark.5 -> hover
 * dark.6 -> normal surfaces
 * dark.7 -> body
 *
 * so this palette is designed around those semantics.
 */
const dark: MantineColorsTuple = [
  '#F3F5F8',
  '#D6DBE3',
  '#B8C0CC',
  '#8F9AAB',
  '#3A4961',
  '#1E2A3D',
  '#111827',
  '#0C111B',
  '#090E17',
  '#060A10'
];

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: {
    light: 6,
    dark: 6
  },
  colors: {
    brand,
    gray,
    dark
  },
  fontFamily: '-apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", sans-serif',
  headings: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", sans-serif',
    fontWeight: '600',
    textWrap: 'balance'
  },
  fontWeights: {
    regular: '400',
    medium: '500',
    bold: '600'
  },
  fontSizes: {
    xs: '0.75rem',
    sm: '0.875rem',
    md: '1rem',
    lg: '1.125rem',
    xl: '1.25rem'
  },
  spacing: {
    xs: '0.5rem', // 8
    sm: '0.75rem', // 12
    md: '1rem', // 16
    lg: '1.25rem', // 20
    xl: '1.5rem' // 24
  },
  radius: {
    xs: '0.375rem', // 6
    sm: '0.5rem', // 8
    md: '0.75rem', // 12
    lg: '1rem', // 16
    xl: '1.375rem' // 22
  },
  defaultRadius: 'md',
  shadows: {
    xs: '0 1px 2px rgb(15 23 42 / 0.04)',
    sm: '0 2px 8px rgb(15 23 42 / 0.06)',
    md: '0 6px 20px rgb(15 23 42 / 0.08)',
    lg: '0 12px 32px rgb(15 23 42 / 0.10)',
    xl: '0 18px 48px rgb(15 23 42 / 0.14)'
  },
  breakpoints: {
    xs: '30em', // 480
    sm: '48em', // 768
    md: '64em', // 1024
    lg: '75em', // 1200
    xl: '90em' // 1440
  },
  focusRing: 'auto',
  cursorType: 'pointer',
  components: {
    Button: Button.extend({
      defaultProps: {
        size: 'md',
        radius: 'md'
      }
    }),
    ActionIcon: ActionIcon.extend({
      defaultProps: {
        size: 'lg',
        radius: 'md',
        variant: 'subtle'
      }
    }),
    /**
     * Mantine v9 propagates Input defaults to TextInput,
     * NumberInput, PasswordInput, Textarea, Select,
     * MultiSelect, etc.
     */
    Input: Input.extend({
      defaultProps: {
        size: 'md',
        radius: 'md',
        variant: 'default'
      }
    }),
    InputWrapper: Input.Wrapper.extend({
      defaultProps: {}
    }),
    Card: Card.extend({
      defaultProps: {
        radius: 'md',
        shadow: undefined
      }
    }),
    Paper: Paper.extend({
      defaultProps: {
        radius: 'md',
        shadow: undefined
      }
    })
  }
});
