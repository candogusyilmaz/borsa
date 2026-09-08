import { useRef } from 'react';

const LEAVE_DEBOUNCE_MS = 150;

/**
 * Returns stable onEnter/onLeave handlers where the leave is debounced.
 * If onEnter fires within LEAVE_DEBOUNCE_MS of a leave, the leave is cancelled.
 */
export function useDebouncedHover(setHoveredIdx: (i: number | null) => void, debounceMs = LEAVE_DEBOUNCE_MS) {
  const leaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const onEnter = (i: number) => {
    if (leaveTimer.current !== null) {
      clearTimeout(leaveTimer.current);
      leaveTimer.current = null;
    }
    setHoveredIdx(i);
  };

  const onLeave = () => {
    leaveTimer.current = setTimeout(() => {
      setHoveredIdx(null);
      leaveTimer.current = null;
    }, debounceMs);
  };

  return { onEnter, onLeave };
}
