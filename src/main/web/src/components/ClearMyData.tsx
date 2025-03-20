import { Button, Modal, Stack, Text, TextInput } from '@mantine/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { create } from 'zustand';
import { mutations } from '~/api';
import { alerts } from '~/lib/alert';

interface ClearMyDataModalState {
  opened: boolean;

  open: () => void;
  close: () => void;
}

export const useClearMyDataModal = create<ClearMyDataModalState>((set) => ({
  opened: false,

  open: () => set({ opened: true }),
  close: () => set({ opened: false })
}));

export function ClearMyDataModal() {
  const { opened, close } = useClearMyDataModal();

  return (
    <Modal centered opened={opened} onClose={close} title="Clear My Data" transitionProps={{ transition: 'fade' }}>
      <ClearMyDataForm />
    </Modal>
  );
}

const confirmText = 'CONFIRM';

function ClearMyDataForm() {
  const client = useQueryClient();
  const { close } = useClearMyDataModal();
  const [confirmInput, setConfirmInput] = useState('');

  const mutation = useMutation({
    ...mutations.account.clearMyData,
    onSuccess: () => {
      close();
      alerts.success('Successfully cleared all of your data.');
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const isConfirmed = confirmInput === confirmText;

  return (
    <Stack>
      <Text size="sm">This action cannot be undone. Please type "{confirmText}" to confirm that you want to clear all of your data.</Text>
      <TextInput placeholder={confirmText} value={confirmInput} onChange={(e) => setConfirmInput(e.currentTarget.value)} />
      <Button
        type="button"
        color="red"
        loading={mutation.isPending || mutation.isSuccess}
        disabled={!isConfirmed}
        onClick={() => mutation.mutate()}>
        Clear My Data
      </Button>
    </Stack>
  );
}
