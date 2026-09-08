import { useMantineColorScheme } from '@mantine/core';

export function useThemeToggle() {
  const { colorScheme, setColorScheme } = useMantineColorScheme();

  const toggleTheme = () => {
    const doc = document as Document & {
      startViewTransition?: (callback: () => void) => void;
    };

    if (!doc.startViewTransition) {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
      return;
    }

    doc.startViewTransition(() => {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
    });
  };

  return { colorScheme, toggleTheme };
}
