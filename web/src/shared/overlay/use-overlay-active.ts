import { useCallback, useSyncExternalStore } from 'react';
import { overlayStore } from './overlay-store';
import type { OverlayDefinition } from './types';

export function useOverlayActive<TProps, TResult = void>(definition: OverlayDefinition<TProps, TResult>): boolean {
  const getSnapshot = useCallback(() => {
    const state = overlayStore.getSnapshot();
    const top = state.stack[state.stack.length - 1];
    return top?.definition === definition;
  }, [definition]);

  return useSyncExternalStore(overlayStore.subscribe, getSnapshot);
}
