import type { MantineTheme } from '@mantine/core';
import { describe, expect, it } from 'vitest';
import { colorSchemeManager, cssVariablesResolver, theme } from './index';

describe('theme configuration', () => {
  it('defines the brand, gray, and dark color palettes', () => {
    expect(theme.primaryColor).toBe('brand');
    expect(theme.primaryShade).toEqual({ light: 6, dark: 6 });
    expect(theme.colors?.brand).toHaveLength(10);
    expect(theme.colors?.gray).toHaveLength(10);
    expect(theme.colors?.dark).toHaveLength(10);

    // Verify brand palette endpoints and key interactive shade
    expect(theme.colors?.brand?.[0]).toBe('#F3F2FF');
    expect(theme.colors?.brand?.[6]).toBe('#625BF6');
    expect(theme.colors?.brand?.[9]).toBe('#342F91');

    // Verify gray palette endpoints
    expect(theme.colors?.gray?.[0]).toBe('#FAFBFC');
    expect(theme.colors?.gray?.[9]).toBe('#171B22');

    // Verify dark palette blue-neutral semantics
    expect(theme.colors?.dark?.[0]).toBe('#F3F5F8'); // text
    expect(theme.colors?.dark?.[6]).toBe('#111827'); // normal surface
    expect(theme.colors?.dark?.[7]).toBe('#0C111B'); // body
    expect(theme.colors?.dark?.[9]).toBe('#060A10');
  });

  it('configures structural tokens according to specification', () => {
    expect(theme.defaultRadius).toBe('md');
    expect(theme.focusRing).toBe('auto');
    expect(theme.cursorType).toBe('pointer');

    expect(theme.fontWeights).toEqual({
      regular: '400',
      medium: '500',
      bold: '600'
    });

    expect(theme.fontSizes).toEqual({
      xs: '0.75rem',
      sm: '0.875rem',
      md: '1rem',
      lg: '1.125rem',
      xl: '1.25rem'
    });

    expect(theme.spacing).toEqual({
      xs: '0.5rem',
      sm: '0.75rem',
      md: '1rem',
      lg: '1.25rem',
      xl: '1.5rem'
    });

    expect(theme.radius).toEqual({
      xs: '0.375rem',
      sm: '0.5rem',
      md: '0.75rem',
      lg: '1rem',
      xl: '1.375rem'
    });

    expect(theme.breakpoints).toEqual({
      xs: '30em',
      sm: '48em',
      md: '64em',
      lg: '75em',
      xl: '90em'
    });
  });

  it('configures component defaults for key Mantine primitives', () => {
    expect(theme.components?.Button?.defaultProps).toEqual({
      size: 'md',
      radius: 'md'
    });

    expect(theme.components?.ActionIcon?.defaultProps).toEqual({
      size: 'lg',
      radius: 'md',
      variant: 'subtle'
    });

    expect(theme.components?.Input?.defaultProps).toEqual({
      size: 'md',
      radius: 'md',
      variant: 'default'
    });

    expect(theme.components?.Card?.defaultProps).toEqual({
      radius: 'md',
      shadow: undefined
    });

    expect(theme.components?.Paper?.defaultProps).toEqual({
      radius: 'md',
      shadow: undefined
    });
  });
});

describe('cssVariablesResolver', () => {
  const resolved = cssVariablesResolver(theme as unknown as MantineTheme);

  it('provides motion tokens', () => {
    expect(resolved.variables).toEqual({
      '--app-motion-fast': '120ms',
      '--app-motion-normal': '160ms',
      '--app-motion-slow': '200ms',
      '--app-ease': 'cubic-bezier(0.2, 0.8, 0.2, 1)'
    });
  });

  it('overrides Mantine semantic variables and defines missing semantics in light mode', () => {
    const light = resolved.light;

    // Mantine semantic variables
    expect(light['--mantine-color-body']).toBe('#F6F7F9');
    expect(light['--mantine-color-text']).toBe('#171B22');
    expect(light['--mantine-color-bright']).toBe('#10141B');
    expect(light['--mantine-color-dimmed']).toBe('#5D6775');
    expect(light['--mantine-color-placeholder']).toBe('#8A95A5');
    expect(light['--mantine-color-anchor']).toBe('var(--mantine-color-brand-7)');

    expect(light['--mantine-color-default']).toBe('#FFFFFF');
    expect(light['--mantine-color-default-hover']).toBe('#F3F4F7');
    expect(light['--mantine-color-default-color']).toBe('#171B22');
    expect(light['--mantine-color-default-border']).toBe('#DCE0E7');

    expect(light['--mantine-color-disabled']).toBe('#F0F2F5');
    expect(light['--mantine-color-disabled-color']).toBe('#9AA4B2');
    expect(light['--mantine-color-disabled-border']).toBe('#E5E8ED');

    expect(light['--mantine-color-error']).toBe('#C83E4D');
    expect(light['--mantine-color-success']).toBe('#16855B');

    expect(light['--mantine-primary-color-filled']).toBe('var(--mantine-color-brand-6)');
    expect(light['--mantine-primary-color-filled-hover']).toBe('var(--mantine-color-brand-7)');
    expect(light['--mantine-primary-color-light']).toBe('rgb(98 91 246 / 0.10)');
    expect(light['--mantine-primary-color-light-hover']).toBe('rgb(98 91 246 / 0.15)');
    expect(light['--mantine-primary-color-light-color']).toBe('var(--mantine-color-brand-7)');

    // Missing Mantine semantics
    expect(light['--app-surface-secondary']).toBe('#F0F2F5');
    expect(light['--app-surface-elevated']).toBe('#FFFFFF');
    expect(light['--app-surface-selected']).toBe('#EFEEFF');
    expect(light['--app-border-subtle']).toBe('#E8EAF0');
    expect(light['--app-border-strong']).toBe('#C8CED8');
    expect(light['--app-focus-ring']).toBe('rgb(98 91 246 / 0.20)');
    expect(light['--app-overlay']).toBe('rgb(10 14 22 / 0.48)');
  });

  it('overrides Mantine semantic variables and defines missing semantics in dark mode', () => {
    const dark = resolved.dark;

    // Mantine semantic variables
    expect(dark['--mantine-color-body']).toBe('#0C111B');
    expect(dark['--mantine-color-text']).toBe('#F3F5F8');
    expect(dark['--mantine-color-bright']).toBe('#FFFFFF');
    expect(dark['--mantine-color-dimmed']).toBe('#AEB7C4');
    expect(dark['--mantine-color-placeholder']).toBe('#7F8A9B');
    expect(dark['--mantine-color-anchor']).toBe('var(--mantine-color-brand-3)');

    expect(dark['--mantine-color-default']).toBe('#111827');
    expect(dark['--mantine-color-default-hover']).toBe('#1A2535');
    expect(dark['--mantine-color-default-color']).toBe('#F3F5F8');
    expect(dark['--mantine-color-default-border']).toBe('#2A374A');

    expect(dark['--mantine-color-disabled']).toBe('#151E2D');
    expect(dark['--mantine-color-disabled-color']).toBe('#667386');
    expect(dark['--mantine-color-disabled-border']).toBe('#202C3F');

    expect(dark['--mantine-color-error']).toBe('#FF7C89');
    expect(dark['--mantine-color-success']).toBe('#58D7A0');

    expect(dark['--mantine-primary-color-filled']).toBe('var(--mantine-color-brand-6)');
    expect(dark['--mantine-primary-color-filled-hover']).toBe('var(--mantine-color-brand-5)');
    expect(dark['--mantine-primary-color-light']).toBe('rgb(169 164 255 / 0.14)');
    expect(dark['--mantine-primary-color-light-hover']).toBe('rgb(169 164 255 / 0.20)');
    expect(dark['--mantine-primary-color-light-color']).toBe('var(--mantine-color-brand-2)');

    // Missing Mantine semantics
    expect(dark['--app-surface-secondary']).toBe('#151E2D');
    expect(dark['--app-surface-elevated']).toBe('#182334');
    expect(dark['--app-surface-selected']).toBe('#24264B');
    expect(dark['--app-border-subtle']).toBe('#202C3F');
    expect(dark['--app-border-strong']).toBe('#3A4961');
    expect(dark['--app-focus-ring']).toBe('rgb(169 164 255 / 0.28)');
    expect(dark['--app-overlay']).toBe('rgb(2 6 12 / 0.68)');
  });
});

describe('colorSchemeManager', () => {
  it('stores and retrieves color scheme using canverse-color-scheme key', () => {
    const storage = new Map<string, string>();
    const mockLocalStorage = {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
      clear: () => storage.clear()
    };
    const testGlobal = globalThis as unknown as {
      window?: {
        localStorage: typeof mockLocalStorage;
        addEventListener: () => void;
        removeEventListener: () => void;
      };
    };
    testGlobal.window = {
      localStorage: mockLocalStorage,
      addEventListener: () => {},
      removeEventListener: () => {}
    };

    try {
      colorSchemeManager.set('dark');
      expect(mockLocalStorage.getItem('canverse-color-scheme')).toBe('dark');
      expect(colorSchemeManager.get('light')).toBe('dark');

      colorSchemeManager.set('light');
      expect(mockLocalStorage.getItem('canverse-color-scheme')).toBe('light');
      expect(colorSchemeManager.get('dark')).toBe('light');

      colorSchemeManager.clear();
      expect(mockLocalStorage.getItem('canverse-color-scheme')).toBeNull();
    } finally {
      delete testGlobal.window;
    }
  });
});
