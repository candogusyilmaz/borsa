import { useComputedColorScheme, useMantineColorScheme } from '@mantine/core';

export function useThemeToggle() {
  const colorScheme = useComputedColorScheme('dark');
  const { setColorScheme } = useMantineColorScheme();

  function toggleTheme() {
    const nextScheme = colorScheme === 'dark' ? 'light' : 'dark';
    if (!document.startViewTransition || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setColorScheme(nextScheme);
      return;
    }
    document.startViewTransition(() => setColorScheme(nextScheme));
  }

  return { colorScheme, toggleTheme };
}
