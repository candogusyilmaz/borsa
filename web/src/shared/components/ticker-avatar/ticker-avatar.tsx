import styles from './ticker-avatar.module.css';

export function TickerAvatar({ symbol, size = 'sm' }: { symbol: string; size?: 'sm' | 'md' }) {
  const monogram = Array.from(symbol.trim()).slice(0, 2).join('').toUpperCase();

  return (
    <span className={styles.avatar} data-size={size} aria-hidden="true">
      {monogram}
    </span>
  );
}
