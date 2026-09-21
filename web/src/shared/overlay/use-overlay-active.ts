import { useCallback, useSyncExternalStore } from 'react';
import { overlayStore } from './overlay-store';
import type { OverlayDefinition } from './types';

export function useOverlayActive<TProps, TResult = void>(definition: OverlayDefinition<TProps, TResult>): boolean {
  const getSnapshot = useCallback(() => {
    const state = overlayStore.getSnapshot();
    if (state.phase !== 'open' || state.stack.length === 0) {
      return false;
    }
    const top = state.stack[state.stack.length - 1];
    return top?.definition === definition;
  }, [definition]);

  return useSyncExternalStore(overlayStore.subscribe, getSnapshot);
}
