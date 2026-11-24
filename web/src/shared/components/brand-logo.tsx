import clsx from 'clsx';
import styles from './brand-logo.module.css';

export function BrandMark({ size = 26, className }: { size?: number; className?: string }) {
  return (
    <svg
      className={clsx(styles.mark, className)}
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true">
      <rect className={styles.tile} width="32" height="32" rx="7.5" />
      <rect className={styles.muted} x="7" y="7" width="8" height="6" rx="1.4" />
      <rect className={styles.fg} x="7" y="15" width="8" height="10" rx="1.4" />
      <rect className={styles.fg} x="17" y="7" width="8" height="18" rx="1.4" />
    </svg>
  );
}

export function BrandLogo({ variant }: { variant?: 'member' }) {
  return (
    <span className={styles.brand} data-variant={variant}>
      <BrandMark size={26} />
      Canverse
    </span>
  );
}
