import type { DefaultMantineColor, MantineColorsTuple } from '@mantine/core';

type AppColor = DefaultMantineColor | 'brand' | 'neutral' | 'success' | 'danger' | 'warning' | 'info';

declare module '@mantine/core' {
  export interface MantineThemeColorsOverride {
    colors: Record<AppColor, MantineColorsTuple>;
  }
}
