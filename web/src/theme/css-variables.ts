import type { CSSVariablesResolver } from '@mantine/core';
import { APP_LAYERS, THEME_PALETTE } from './tokens';

const { light, dark } = THEME_PALETTE;

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
    '--mobile-bottom-nav-height': '3.75rem',
    '--app-layer-language-selector': String(APP_LAYERS.languageSelector),
    '--app-layer-desktop-header': String(APP_LAYERS.desktopHeader),
    '--app-layer-mobile-status-bar': String(APP_LAYERS.mobileStatusBar),
    '--app-layer-bottom-nav': String(APP_LAYERS.bottomNav),
    '--app-layer-skip-link': String(APP_LAYERS.skipLink)
  },
  light: scheme({
    body: light.canvas,
    surface: light.surface,
    elevated: light.raised,
    secondary: light.raised,
    hover: light.raised,
    border: light.border,
    strong: light.strongBorder,
    text: light.text,
    dimmed: light.dimmed,
    placeholder: light.placeholder,
    primary: light.primary,
    onPrimary: light.surface,
    accent: '#4338CA',
    chartLine: '#526781',
    weightFill: '#78756D',
    financeOpacity: 10,
    profit: light.profit,
    loss: light.loss,
    overlay: 'rgb(28 27 24 / 0.42)'
  }),
  dark: scheme({
    body: dark.canvas,
    surface: dark.surface,
    elevated: dark.raised,
    secondary: '#1C1C21',
    hover: dark.raised,
    border: dark.border,
    strong: dark.strongBorder,
    text: dark.text,
    dimmed: dark.dimmed,
    placeholder: dark.placeholder,
    primary: dark.text,
    onPrimary: dark.canvas,
    accent: '#818CF8',
    chartLine: '#A7B7CD',
    weightFill: '#82828B',
    financeOpacity: 12,
    profit: dark.profit,
    loss: dark.loss,
    overlay: 'rgb(0 0 0 / 0.62)'
  })
});
