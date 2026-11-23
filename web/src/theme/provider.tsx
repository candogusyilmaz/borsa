import { localStorageColorSchemeManager, MantineProvider } from '@mantine/core';
import type { ReactNode } from 'react';
import { cssVariablesResolver } from './css-variables';
import { theme } from './theme';
import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/charts/styles.css';
import '@mantine/notifications/styles.css';
import './theme.css';

export const colorSchemeManager = localStorageColorSchemeManager({
  key: 'mantine-color-scheme-value'
});

export function AppThemeProvider({ children }: { children: ReactNode }) {
  return (
    <MantineProvider
      theme={theme}
      cssVariablesResolver={cssVariablesResolver}
      colorSchemeManager={colorSchemeManager}
      defaultColorScheme="auto">
      {children}
    </MantineProvider>
  );
}
