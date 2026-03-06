import { Modal, ScrollArea } from '@mantine/core';
import { create } from 'zustand';
import type { paths } from '~/api/schema';
import { UndoTradeForm } from './UndoTradeForm';

type Trade = paths['/api/portfolios/{portfolioId}/trades']['get']['responses']['200']['content']['*/*']['trades'][number];

interface UndoTradeModalState {
  opened: boolean;
  trade: Trade;

  // Actions
  open: (trade: Trade) => void;
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
