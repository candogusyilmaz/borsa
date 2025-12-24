import { Modal, ScrollArea, SegmentedControl } from '@mantine/core';
import { create } from 'zustand';
import { BuyTransactionForm } from './BuyTransactionForm';
import { SellTransactionForm } from './SellTransactionForm';

type TransactionType = 'Buy' | 'Sell';

interface TransactionModalState {
  opened: boolean;
  type: TransactionType;
  stockId?: string;

  // Actions
  open: (type: TransactionType, stockId?: string) => void;
  close: () => void;
  update: (value: Partial<TransactionModalState>) => void;
}

export const useTransactionModalStore = create<TransactionModalState>((set) => ({
  // Initial state
  opened: false,
  type: 'Buy',
  stockId: undefined,

  // Actions
  open: (type, stockId) => set({ opened: true, type, stockId }),
  close: () => set({ opened: false }),
  update: (value) => set(value)
}));

export function TransactionModal() {
  const { opened, type, stockId, close, update } = useTransactionModalStore();

  const handleTypeChange = (value: string) => {
    update({ type: value as TransactionType, stockId: undefined });
  };

  return (
    <Modal
      centered
      opened={opened}
      onClose={close}
      title="New Trade"
      transitionProps={{ transition: 'fade' }}
      scrollAreaComponent={ScrollArea.Autosize}>
      <SegmentedControl
        value={type}
        onChange={handleTypeChange}
        color={type === 'Buy' ? 'teal' : 'red'}
        fullWidth
        size="sm"
        data={['Buy', 'Sell']}
        mb="md"
      />
      {type === 'Buy' && <BuyTransactionForm stockId={stockId} close={close} />}
      {type === 'Sell' && <SellTransactionForm stockId={stockId} close={close} />}
    </Modal>
  );
}
