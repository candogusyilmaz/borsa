import { IconTrendingUp } from '@tabler/icons-react';
import styles from './brand-logo.module.css';

export function BrandLogo({ variant }: { variant?: 'member' }) {
  return (
    <span className={styles.brand} data-variant={variant}>
      <span className={styles.mark} aria-hidden="true">
        <IconTrendingUp size={17} stroke={1.7} />
      </span>
      Canverse
    </span>
  );
}
