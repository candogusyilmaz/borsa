import type { CSSVariablesResolver } from '@mantine/core';
import { semantic } from './semantic';

type SemanticValues = typeof semantic.light | typeof semantic.dark;

function toVars(v: SemanticValues): Record<string, string> {
  return {
    // Bridge Mantine's own semantic variables to our palette so all components auto-theme
    '--mantine-color-body': v.surface,
    '--mantine-color-text': v.textPrimary,
    '--mantine-color-placeholder': v.textTertiary,
    '--mantine-color-dimmed': v.textSecondary,
    '--mantine-color-default': v.surfaceInteractive,
    '--mantine-color-default-hover': v.surfaceHover,
    '--mantine-color-default-border': v.border,
    '--mantine-color-default-color': v.textPrimary,
    '--mantine-color-anchor': v.accent,
    '--mantine-color-error': v.danger,

    '--app-bg': v.bg,

    '--app-surface': v.surface,
    '--app-surface-secondary': v.surfaceSecondary,
    '--app-surface-elevated': v.surfaceElevated,
    '--app-surface-interactive': v.surfaceInteractive,
    '--app-surface-hover': v.surfaceHover,
    '--app-surface-selected': v.surfaceSelected,

    '--app-text-primary': v.textPrimary,
    '--app-text-secondary': v.textSecondary,
    '--app-text-tertiary': v.textTertiary,
    '--app-text-disabled': v.textDisabled,

    '--app-border-subtle': v.borderSubtle,
    '--app-border': v.border,
    '--app-border-strong': v.borderStrong,

    '--app-accent': v.accent,
    '--app-accent-fill': v.accentFill,
    '--app-accent-hover': v.accentHover,
    '--app-accent-subtle': v.accentSubtle,

    '--app-success': v.success,
    '--app-danger': v.danger,
    '--app-warning': v.warning,
    '--app-info': v.info,

    '--app-focus-ring': v.focusRing,
    '--app-overlay': v.overlay,

    '--app-shadow-raised': v.shadowRaised,
    '--app-shadow-floating': v.shadowFloating
  };
}

export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {
    '--app-space-1': '0.25rem',
    '--app-space-2': '0.5rem',
    '--app-space-3': '0.75rem',
    '--app-space-4': '1rem',
    '--app-space-5': '1.25rem',
    '--app-space-6': '1.5rem',
    '--app-space-7': '2rem',
    '--app-space-8': '2.5rem',
    '--app-space-9': '3rem',

    '--app-motion-fast': '120ms',
    '--app-motion-standard': '160ms',
    '--app-motion-slow': '200ms',
    '--app-ease-standard': 'cubic-bezier(0.2, 0.8, 0.2, 1)'
  },

  light: toVars(semantic.light),
  dark: toVars(semantic.dark)
});
