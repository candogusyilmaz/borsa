import { useEffect, useState } from 'react';

export function useContainerWidth(ref: { current: HTMLElement | null }) {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      if (entries[0]) setWidth(entries[0].contentRect.width);
    });
    ro.observe(el);
    setWidth(el.offsetWidth);
    return () => ro.disconnect();
  }, [ref]);
  return width;
}
