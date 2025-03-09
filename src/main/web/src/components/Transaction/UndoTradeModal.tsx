import { Modal, ScrollArea } from '@mantine/core';
import { create } from 'zustand';
import type { TradeHistoryTrade } from '~/api/queries/types';
import { UndoTradeForm } from './UndoTradeForm';

interface UndoTradeModalState {
  opened: boolean;
  trade: TradeHistoryTrade;

  // Actions
  open: (trade: TradeHistoryTrade) => void;
  close: () => void;
}

export const useUndoTradeModalStore = create<UndoTradeModalState>((set) => ({
  // Initial state
  opened: false,
  trade: null!,

  // Actions
  open: (trade) => set({ opened: true, trade }),
  close: () => set({ opened: false })
}));

export function UndoTradeModal() {
  const { opened, close } = useUndoTradeModalStore();

  return (
    <Modal
      centered
      withCloseButton={false}
      opened={opened}
      onClose={close}
      transitionProps={{ transition: 'fade' }}
      scrollAreaComponent={ScrollArea.Autosize}>
      <UndoTradeForm />
    </Modal>
  );
}
