import { ActionIcon, Group, Modal, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconPlus, IconRowRemove, IconX } from '@tabler/icons-react';
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
  const { opened, type, stockId, close } = useTransactionModalStore();

  return (
    <Modal
      centered
      opened={opened}
      onClose={close}
      withCloseButton={false}
      transitionProps={{ transition: 'fade' }}
      size="500"
      classNames={{
        content: 'no-scrollbar'
      }}
      styles={{
        content: {
          padding: '1.5rem'
        }
      }}>
      <Group mb="xl">
        <ThemeIcon radius="lg" variant="light" size="xl" color={type === 'Buy' ? 'teal' : 'red'}>
          {type === 'Buy' ? <IconPlus /> : <IconRowRemove />}
        </ThemeIcon>
        <Stack gap={0}>
          <Title order={3} size="md" c="var(--text-main)">
            {type.toUpperCase()} ASSET
          </Title>
          <Text size="sm" c="dimmed">
            Enter details to {type.toLowerCase()} an asset
          </Text>
        </Stack>
        <ActionIcon onClick={close} variant="subtle" ml="auto" color="gray">
          <IconX size={18} />
        </ActionIcon>
      </Group>
      {type === 'Buy' && <BuyTransactionForm stockId={stockId} close={close} />}
      {type === 'Sell' && <SellTransactionForm stockId={stockId} close={close} />}
    </Modal>
  );
}
