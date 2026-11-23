import { memo, useEffect, useRef, useState } from 'react';
import classes from './bulk-trade-modal.module.css';

interface TypePillProps {
  value: 'BUY' | 'SELL';
  onChange: (v: 'BUY' | 'SELL') => void;
}

const TYPE_COLORS = {
  BUY: { dot: 'var(--mantine-color-profit-5)' },
  SELL: { dot: 'var(--mantine-color-loss-5)' }
} as const;

export const TypePill = memo(function TypePill({ value, onChange }: TypePillProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative', width: '100%' }}>
      <button
        type="button"
        className={`${classes.typePill} ${value === 'BUY' ? classes.typePillBuy : classes.typePillSell}`}
        onClick={() => setOpen((o) => !o)}>
        <span className={classes.typeDot} style={{ background: TYPE_COLORS[value].dot }} />
        {value}
        <svg className={classes.typeChevron} width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true">
          <path d="M2 3.5l3 3 3-3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      </button>
      {open && (
        <div className={classes.typeDropdown}>
          {(['BUY', 'SELL'] as const).map((v) => (
            <button
              type="button"
              key={v}
              className={classes.typeOption}
              style={{ color: TYPE_COLORS[v].dot }}
              onMouseDown={() => {
                onChange(v);
                setOpen(false);
              }}>
              <span className={classes.typeDot} style={{ background: TYPE_COLORS[v].dot }} />
              {v}
            </button>
          ))}
        </div>
      )}
    </div>
  );
});
