import { localStorageColorSchemeManager, MantineProvider, useComputedColorScheme } from '@mantine/core';
import { type ReactNode, useEffect } from 'react';
import { cssVariablesResolver } from './css-variables';
import { theme } from './theme';
import { THEME_PALETTE } from './tokens';
import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/charts/styles.css';
import '@mantine/notifications/styles.css';
import './theme.css';

export const colorSchemeManager = localStorageColorSchemeManager({
  key: 'mantine-color-scheme-value'
});

function ThemeColorSync() {
  const colorScheme = useComputedColorScheme('light');

  useEffect(() => {
    const color = THEME_PALETTE[colorScheme].canvas;
    const metas = document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]');

    if (metas.length === 0) {
      const meta = document.createElement('meta');
      meta.name = 'theme-color';
      meta.content = color;
      document.head.appendChild(meta);
      return;
    }

    metas.forEach((meta, index) => {
      if (index === 0) {
        meta.removeAttribute('media');
        meta.content = color;
      } else {
        meta.remove();
      }
    });
  }, [colorScheme]);

  return null;
}

export function AppThemeProvider({ children }: { children: ReactNode }) {
  return (
    <MantineProvider
      theme={theme}
      cssVariablesResolver={cssVariablesResolver}
      colorSchemeManager={colorSchemeManager}
      defaultColorScheme="auto">
      <ThemeColorSync />
      {children}
    </MantineProvider>
  );
}
