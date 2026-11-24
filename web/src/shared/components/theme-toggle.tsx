import { useLingui } from '@lingui/react/macro';
import { IconMoon, IconSun } from '@tabler/icons-react';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import styles from './theme-toggle.module.css';

export function ThemeToggle() {
  const { t } = useLingui();
  const { colorScheme, toggleTheme } = useThemeToggle();
  const isDark = colorScheme === 'dark';
  const label = isDark ? t`Switch to light mode` : t`Switch to dark mode`;

  return (
    <button type="button" className={styles.button} onClick={toggleTheme} aria-label={label} title={label}>
      {isDark ? <IconSun size={16} stroke={1.7} aria-hidden="true" /> : <IconMoon size={16} stroke={1.7} aria-hidden="true" />}
    </button>
  );
}
