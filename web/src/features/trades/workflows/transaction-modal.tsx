import { create } from 'zustand';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { BuyTransactionForm } from './buy-transaction-form';
import { SellTransactionForm } from './sell-transaction-form';

type TransactionType = 'Buy' | 'Sell';

interface TransactionModalState {
  opened: boolean;
  type: TransactionType;
  stockId?: string;
  portfolioId?: string;
  open: (type: TransactionType, stockId?: string, portfolioId?: string) => void;
  close: () => void;
  update: (value: Partial<TransactionModalState>) => void;
}

export const useTransactionModalStore = create<TransactionModalState>((set) => ({
  opened: false,
  type: 'Buy',
  stockId: undefined,
  open: (type, stockId, portfolioId) => set({ opened: true, type, stockId, portfolioId }),
  close: () => set({ opened: false }),
  update: (value) => set(value)
}));

export function TransactionModal() {
  const { opened, type, stockId, portfolioId, close } = useTransactionModalStore();

  return (
    <ResponsiveOverlay opened={opened} onClose={close} title={`${type} asset`} size={500}>
      {type === 'Buy' && <BuyTransactionForm stockId={stockId} portfolioId={portfolioId} close={close} />}
      {type === 'Sell' && <SellTransactionForm stockId={stockId} portfolioId={portfolioId} close={close} />}
    </ResponsiveOverlay>
  );
}
