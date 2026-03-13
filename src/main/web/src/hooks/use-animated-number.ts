import { useEffect, useRef, useState } from 'react';

export function useAnimatedNumber(value: number) {
  const [display, setDisplay] = useState(value);
  const prevRef = useRef(value);

  useEffect(() => {
    // Store the frame ID to allow cancellation
    let frameId: number;

    const start = prevRef.current;
    const end = value;
    const duration = 500;
    const t0 = performance.now();

    const tick = (now: number) => {
      const p = Math.min((now - t0) / duration, 1);
      // Cubic ease-out
      const ease = 1 - (1 - p) ** 3;

      setDisplay(start + (end - start) * ease);

      if (p < 1) {
        frameId = requestAnimationFrame(tick);
      } else {
        prevRef.current = end;
      }
    };

    frameId = requestAnimationFrame(tick);

    // Cleanup function runs on unmount or when dependencies change
    return () => cancelAnimationFrame(frameId);
  }, [value]);

  return display;
}
