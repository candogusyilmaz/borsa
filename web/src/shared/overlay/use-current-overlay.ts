import { createContext, useContext } from 'react';
import type { CurrentOverlayContextValue } from './types';

export const CurrentOverlayContext = createContext<CurrentOverlayContextValue<unknown> | null>(null);

export function useCurrentOverlayInternal(): CurrentOverlayContextValue<unknown> {
  const context = useContext(CurrentOverlayContext);
  if (!context) {
    throw new Error('Overlay hook must be used within an active overlay component rendered by OverlayHost');
  }
  return context;
}
