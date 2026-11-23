import { create } from 'zustand';
import type { components } from '~/api/schema';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { UndoTradeForm } from './undo-trade-form';

type Trade = components['schemas']['Item'];

interface UndoTradeModalState {
  opened: boolean;
  trade: Trade;
  portfolioId?: string;
  currencyCode?: string;
  open: (trade: Trade, portfolioId?: string, currencyCode?: string) => void;
  close: () => void;
}

export const useUndoTradeModalStore = create<UndoTradeModalState>((set) => ({
  opened: false,
  trade: null!,
  open: (trade, portfolioId, currencyCode) => set({ opened: true, trade, portfolioId, currencyCode }),
  close: () => set({ opened: false })
}));

export function UndoTradeModal() {
  const { opened, close } = useUndoTradeModalStore();
  return (
    <ResponsiveOverlay opened={opened} onClose={close} title="Undo trade">
      <UndoTradeForm />
    </ResponsiveOverlay>
  );
}
