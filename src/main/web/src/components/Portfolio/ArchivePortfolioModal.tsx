import { Button, Group, Modal, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { IconAlertTriangle, IconArchive } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { create } from 'zustand';
import { mutations } from '~/api';

interface ArchivePortfolioModalState {
  opened: boolean;
  portfolioId: string;

  // Actions
  open: (portfolioId: string) => void;
  close: () => void;
}

export const useArchivePortfolioModalStore = create<ArchivePortfolioModalState>((set) => ({
  // Initial state
  opened: false,
  portfolioId: null!,

  // Actions
  open: (portfolioId) => set({ opened: true, portfolioId }),
  close: () => set({ opened: false })
}));

export function ArchivePortfolioModal() {
  const { opened, close } = useArchivePortfolioModalStore();

  return (
    <Modal centered withCloseButton={false} opened={opened} onClose={close} size="lg" aria-labelledby="archive-portfolio-title">
      <ArchivePortfolioForm />
    </Modal>
  );
}

function ArchivePortfolioForm() {
  const { portfolioId, close } = useArchivePortfolioModalStore();
  const [confirmText, setConfirmText] = useState('');
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const mutation = useMutation({
    ...mutations.portfolio.archivePortfolio,
    onSuccess: () => {
      close();
      queryClient.invalidateQueries();
      navigate({ to: '/dashboard' });
    }
  });

  const handleFormSubmit = () => {
    if (!portfolioId) return;
    mutation.mutate(portfolioId);
  };

  const confirmationRequired = 'ARCHIVE';
  const isConfirmed = confirmText.trim().toUpperCase() === confirmationRequired;

  return (
    <Stack>
      <Group>
        <ThemeIcon color="red">
          <IconArchive style={{ width: '70%', height: '70%' }} />
        </ThemeIcon>

        <Title fz="lg">Archive portfolio</Title>
      </Group>

      <Text c="dimmed">
        Archiving will remove this portfolio from active lists and hide its positions. It can be recovered by an admin only. This action
        cannot be undone.
      </Text>

      <Text size="sm">
        To confirm, type <strong>{confirmationRequired}</strong> in the box below.
      </Text>

      <TextInput
        placeholder={confirmationRequired}
        value={confirmText}
        onChange={(e) => setConfirmText(e.currentTarget.value)}
        disabled={mutation.isPending || mutation.isSuccess}
        data-autofocus
        aria-label="Type ARCHIVE to confirm"
      />

      <Group justify="flex-end">
        <Button variant="subtle" color="gray" onClick={close} disabled={mutation.isPending}>
          Cancel
        </Button>

        <Button
          color="red"
          onClick={handleFormSubmit}
          loading={mutation.isPending}
          disabled={!isConfirmed || mutation.isPending || mutation.isSuccess}>
          Archive Portfolio
        </Button>
      </Group>
    </Stack>
  );
}
