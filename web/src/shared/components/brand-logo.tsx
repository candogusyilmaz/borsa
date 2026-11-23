import { IconTrendingUp } from '@tabler/icons-react';
import styles from './brand-logo.module.css';

export function BrandLogo() {
  return (
    <span className={styles.brand}>
      <span className={styles.mark} aria-hidden="true">
        <IconTrendingUp size={17} stroke={1.7} />
      </span>
      Canverse
    </span>
  );
}
