import type { ReactNode } from 'react';

export interface OverlayProviderProps {
  children: ReactNode;
}

export function OverlayProvider({ children }: OverlayProviderProps) {
  return <>{children}</>;
}
