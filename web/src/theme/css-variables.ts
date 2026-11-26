import type { CSSVariablesResolver } from '@mantine/core';

const scheme = (colors: {
  body: string;
  surface: string;
  elevated: string;
  secondary: string;
  hover: string;
  border: string;
  strong: string;
  text: string;
  dimmed: string;
  placeholder: string;
  primary: string;
  onPrimary: string;
  accent: string;
  chartLine: string;
  weightFill: string;
  financeOpacity: number;
  profit: string;
  loss: string;
  overlay: string;
}) => ({
  '--mantine-color-body': colors.body,
  '--mantine-color-default': colors.surface,
  '--mantine-color-default-hover': colors.hover,
  '--mantine-color-default-border': colors.border,
  '--mantine-color-default-color': colors.text,
  '--mantine-color-text': colors.text,
  '--mantine-color-bright': colors.text,
  '--mantine-color-dimmed': colors.dimmed,
  '--mantine-color-placeholder': colors.placeholder,
  '--mantine-color-anchor': colors.accent,
  '--mantine-color-error': colors.loss,
  '--mantine-color-success': colors.profit,
  '--mantine-color-disabled': colors.secondary,
  '--mantine-color-disabled-color': colors.dimmed,
  '--mantine-color-disabled-border': colors.border,
  '--mantine-color-brand-filled': colors.primary,
  '--mantine-color-brand-filled-hover': `color-mix(in srgb, ${colors.primary}, ${colors.onPrimary} 14%)`,
  '--mantine-color-brand-light': 'var(--app-surface-selected)',
  '--mantine-color-brand-light-hover': 'var(--mantine-color-default-hover)',
  '--mantine-color-brand-light-color': colors.text,
  '--mantine-color-brand-outline': colors.text,
  '--mantine-color-brand-outline-hover': colors.hover,
  '--mantine-color-profit-light': 'var(--app-finance-profit-bg)',
  '--mantine-color-profit-light-color': colors.profit,
  '--mantine-color-loss-light': 'var(--app-finance-loss-bg)',
  '--mantine-color-loss-light-color': colors.loss,
  '--app-on-primary': colors.onPrimary,
  '--app-accent': colors.accent,
  '--app-surface-elevated': colors.elevated,
  '--app-surface-secondary': colors.secondary,
  '--app-surface-selected': colors.secondary,
  '--app-chart-line': colors.chartLine,
  '--app-weight-fill': colors.weightFill,
  '--app-border-subtle': colors.border,
  '--app-border-strong': colors.strong,
  '--app-finance-profit': colors.profit,
  '--app-finance-loss': colors.loss,
  '--app-finance-profit-bg': `color-mix(in srgb, ${colors.profit} ${colors.financeOpacity}%, transparent)`,
  '--app-finance-loss-bg': `color-mix(in srgb, ${colors.loss} ${colors.financeOpacity}%, transparent)`,
  '--app-overlay': colors.overlay
});

export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {
    '--app-ease': 'cubic-bezier(0.2, 0.8, 0.2, 1)',
    '--app-motion-slow': '200ms',
    '--app-content-width': '74rem',
    '--mobile-bottom-nav-height': '3.75rem'
  },
  light: scheme({
    body: '#F3F2EE',
    surface: '#FAF9F6',
    elevated: '#EAE8E1',
    secondary: '#EAE8E1',
    hover: '#EAE8E1',
    border: '#E2DFD7',
    strong: '#CFCBC0',
    text: '#1C1B18',
    dimmed: '#6E6B64',
    placeholder: '#9A968C',
    primary: '#242320',
    onPrimary: '#FAF9F6',
    accent: '#4338CA',
    chartLine: '#526781',
    weightFill: '#78756D',
    financeOpacity: 10,
    profit: '#15803D',
    loss: '#BE123C',
    overlay: 'rgb(28 27 24 / 0.42)'
  }),
  dark: scheme({
    body: '#0D0D0F',
    surface: '#131316',
    elevated: '#1B1B20',
    secondary: '#1C1C21',
    hover: '#1B1B20',
    border: '#222228',
    strong: '#34343D',
    text: '#F4F4F5',
    dimmed: '#8E8E98',
    placeholder: '#63636E',
    primary: '#F4F4F5',
    onPrimary: '#0D0D0F',
    accent: '#818CF8',
    chartLine: '#A7B7CD',
    weightFill: '#82828B',
    financeOpacity: 12,
    profit: '#34D399',
    loss: '#FB7185',
    overlay: 'rgb(0 0 0 / 0.62)'
  })
});
