import { ActionIcon, useComputedColorScheme, useMantineColorScheme } from '@mantine/core';
import { MoonIcon, SunIcon } from '@phosphor-icons/react';

export function ThemeToggle() {
  const { setColorScheme } = useMantineColorScheme();
  const computedColorScheme = useComputedColorScheme('light', { getInitialValueInEffect: true });

  const isDark = computedColorScheme === 'dark';

  return (
    <ActionIcon
      variant="default"
      size="lg"
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      onClick={() => setColorScheme(isDark ? 'light' : 'dark')}>
      {isDark ? <SunIcon size={18} weight="bold" /> : <MoonIcon size={18} weight="bold" />}
    </ActionIcon>
  );
}
