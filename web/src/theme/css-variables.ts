import type { CSSVariablesResolver } from '@mantine/core';

export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {
    /*
     * Mantine does not have generic motion tokens.
     * These are worth owning ourselves.
     */
    '--app-motion-fast': '120ms',
    '--app-motion-normal': '160ms',
    '--app-motion-slow': '200ms',
    '--app-ease': 'cubic-bezier(0.2, 0.8, 0.2, 1)',
    '--mobile-bottom-nav-height': '4rem'
  },
  light: {
    /*
     * --------------------------------------------------------
     * MANTINE SEMANTIC VARIABLES
     * --------------------------------------------------------
     */
    // Application canvas
    '--mantine-color-body': '#F6F7F9',

    // Main typography
    '--mantine-color-text': '#171B22',
    '--mantine-color-bright': '#10141B',

    // Secondary text
    '--mantine-color-dimmed': '#5D6775',

    // Form placeholder
    '--mantine-color-placeholder': '#8A95A5',

    // Links
    '--mantine-color-anchor': 'var(--mantine-color-brand-7)',

    /*
     * "default" is extremely useful.
     *
     * Button variant="default", ActionIcon default,
     * inputs and a number of other Mantine components
     * derive their neutral presentation from these.
     */
    '--mantine-color-default': '#FFFFFF',
    '--mantine-color-default-hover': '#F3F4F7',
    '--mantine-color-default-color': '#171B22',
    '--mantine-color-default-border': '#DCE0E7',

    // Disabled state
    '--mantine-color-disabled': '#F0F2F5',
    '--mantine-color-disabled-color': '#9AA4B2',
    '--mantine-color-disabled-border': '#E5E8ED',

    // Semantic states
    '--mantine-color-error': '#C83E4D',
    '--mantine-color-success': '#16855B',

    /*
     * Primary interaction color.
     *
     * These drive filled/light variants in many Mantine
     * components, not only Button.
     */
    '--mantine-primary-color-filled': 'var(--mantine-color-brand-6)',
    '--mantine-primary-color-filled-hover': 'var(--mantine-color-brand-7)',
    '--mantine-primary-color-light': 'rgb(98 91 246 / 0.10)',
    '--mantine-primary-color-light-hover': 'rgb(98 91 246 / 0.15)',
    '--mantine-primary-color-light-color': 'var(--mantine-color-brand-7)',

    /*
     * --------------------------------------------------------
     * ONLY THE SEMANTICS MANTINE DOES NOT ALREADY PROVIDE
     * --------------------------------------------------------
     */
    '--app-surface-secondary': '#F0F2F5',
    '--app-surface-elevated': '#FFFFFF',
    '--app-surface-selected': '#EFEEFF',
    '--app-border-subtle': '#E8EAF0',
    '--app-border-strong': '#C8CED8',
    '--app-focus-ring': 'rgb(98 91 246 / 0.20)',
    '--app-overlay': 'rgb(10 14 22 / 0.48)'
  },
  dark: {
    /*
     * --------------------------------------------------------
     * MANTINE SEMANTIC VARIABLES
     * --------------------------------------------------------
     */
    '--mantine-color-body': '#0C111B',
    '--mantine-color-text': '#F3F5F8',
    '--mantine-color-bright': '#FFFFFF',
    '--mantine-color-dimmed': '#AEB7C4',
    '--mantine-color-placeholder': '#7F8A9B',
    '--mantine-color-anchor': 'var(--mantine-color-brand-3)',

    '--mantine-color-default': '#111827',
    '--mantine-color-default-hover': '#1A2535',
    '--mantine-color-default-color': '#F3F5F8',
    '--mantine-color-default-border': '#2A374A',

    '--mantine-color-disabled': '#151E2D',
    '--mantine-color-disabled-color': '#667386',
    '--mantine-color-disabled-border': '#202C3F',

    '--mantine-color-error': '#FF7C89',
    '--mantine-color-success': '#58D7A0',

    /*
     * Keep filled buttons slightly darker.
     * Links/selection can use the brighter brand shades.
     */
    '--mantine-primary-color-filled': 'var(--mantine-color-brand-6)',
    '--mantine-primary-color-filled-hover': 'var(--mantine-color-brand-5)',
    '--mantine-primary-color-light': 'rgb(169 164 255 / 0.14)',
    '--mantine-primary-color-light-hover': 'rgb(169 164 255 / 0.20)',
    '--mantine-primary-color-light-color': 'var(--mantine-color-brand-2)',

    /*
     * --------------------------------------------------------
     * MISSING MANTINE SEMANTICS
     * --------------------------------------------------------
     */
    '--app-surface-secondary': '#151E2D',
    '--app-surface-elevated': '#182334',
    '--app-surface-selected': '#24264B',
    '--app-border-subtle': '#202C3F',
    '--app-border-strong': '#3A4961',
    '--app-focus-ring': 'rgb(169 164 255 / 0.28)',
    '--app-overlay': 'rgb(2 6 12 / 0.68)'
  }
});
