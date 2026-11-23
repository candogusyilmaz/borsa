import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Stack, Text, TextInput } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { create } from 'zustand';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { alerts } from '~/shared/feedback/alerts';

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
  const { t } = useLingui();
  const { opened, close } = useClearMyDataModal();

  return (
    <ResponsiveOverlay opened={opened} onClose={close} title={t`Clear my data`}>
      <ClearMyDataForm />
    </ResponsiveOverlay>
  );
}

const confirmText = 'CONFIRM';

function ClearMyDataForm() {
  const { t } = useLingui();
  const client = useQueryClient();
  const { close } = useClearMyDataModal();
  const [confirmInput, setConfirmInput] = useState('');

  const mutation = $api.useMutation('post', '/api/account/clear-my-data', {
    onSuccess: () => {
      close();
      alerts.success(t`Successfully cleared all of your data.`);
      client.invalidateQueries();
    },
    onError: () => alerts.error(t`Could not clear your data. Try again.`)
  });

  const isConfirmed = confirmInput === confirmText;

  return (
    <Stack>
      <Text size="sm">
        <Trans comment="The confirmation code is a fixed string inserted by the application.">
          This action cannot be undone. Please type <strong>{confirmText}</strong> to confirm that you want to clear all of your data.
        </Trans>
      </Text>
      <TextInput
        aria-label={t`Confirmation code`}
        placeholder={confirmText}
        value={confirmInput}
        onChange={(e) => setConfirmInput(e.currentTarget.value)}
      />
      <Button
        type="button"
        color="red"
        loading={mutation.isPending || mutation.isSuccess}
        disabled={!isConfirmed}
        onClick={() => mutation.mutate({})}>
        <Trans>Clear My Data</Trans>
      </Button>
    </Stack>
  );
}
