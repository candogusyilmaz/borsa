import { createContext, useContext } from 'react';
import type { CurrentOverlayContextValue } from './types';

export const CurrentOverlayContext = createContext<CurrentOverlayContextValue<unknown> | null>(null);

export function useCurrentOverlay<TResult = void>(): CurrentOverlayContextValue<TResult> {
  const context = useContext(CurrentOverlayContext);
  if (!context) {
    throw new Error('useCurrentOverlay must be used within an active overlay component rendered by OverlayHost');
  }
  return context as unknown as CurrentOverlayContextValue<TResult>;
}
