import { createContext, type ReactNode, useContext, useState } from 'react';

export type AccountDetailOverlay =
  | { type: 'actions' }
  | { type: 'info' }
  | { type: 'activities' }
  | { type: 'transfer' }
  | { type: 'settings' }
  | { type: 'archive' }
  | { type: 'opening-correction' };

interface AccountDetailOverlayContextValue {
  active: AccountDetailOverlay | null;
  open: (overlay: AccountDetailOverlay) => void;
  close: () => void;
}

const AccountDetailOverlayContext = createContext<AccountDetailOverlayContextValue | null>(null);

export function useAccountDetailOverlay() {
  const context = useContext(AccountDetailOverlayContext);
  if (!context) {
    throw new Error('useAccountDetailOverlay must be used within an AccountDetailOverlayProvider');
  }
  return context;
}

export function useOptionalAccountDetailOverlay() {
  return useContext(AccountDetailOverlayContext);
}

export function AccountDetailOverlayProvider({ children }: { children: ReactNode }) {
  const [active, setActive] = useState<AccountDetailOverlay | null>(null);

  return (
    <AccountDetailOverlayContext.Provider value={{ active, open: setActive, close: () => setActive(null) }}>
      {children}
    </AccountDetailOverlayContext.Provider>
  );
}
