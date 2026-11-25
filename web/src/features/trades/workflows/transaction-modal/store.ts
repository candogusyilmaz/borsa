import { create } from 'zustand';

export type TransactionType = 'Buy' | 'Sell';

interface TransactionModalState {
  opened: boolean;
  sessionId: number;
  type: TransactionType;
  stockId?: string;
  portfolioId?: string;
  open: (type: TransactionType, stockId?: string, portfolioId?: string) => void;
  close: () => void;
  update: (value: Partial<TransactionModalState>) => void;
}

export const useTransactionModalStore = create<TransactionModalState>((set) => ({
  opened: false,
  sessionId: 0,
  type: 'Buy',
  stockId: undefined,
  portfolioId: undefined,
  open: (type, stockId, portfolioId) => set((state) => ({ opened: true, sessionId: state.sessionId + 1, type, stockId, portfolioId })),
  close: () => set({ opened: false }),
  update: (value) => set(value)
}));
