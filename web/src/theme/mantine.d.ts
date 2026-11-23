import type { DefaultMantineColor, MantineColorsTuple } from '@mantine/core';

type ExtendedCustomColors = 'brand' | 'profit' | 'loss' | DefaultMantineColor;

declare module '@mantine/core' {
  export interface MantineThemeColorsOverride {
    colors: Record<ExtendedCustomColors, MantineColorsTuple>;
  }
}
