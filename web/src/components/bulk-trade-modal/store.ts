import { create } from 'zustand';

interface BulkTradeModalState {
  opened: boolean;
  portfolioId: number;

  open: (portfolioId: number) => void;
  close: () => void;
}

export const useBulkTradeModalStore = create<BulkTradeModalState>((set) => ({
  opened: false,
  portfolioId: null!,
  open: (portfolioId) => set({ opened: true, portfolioId }),
  close: () => set({ opened: false })
}));
