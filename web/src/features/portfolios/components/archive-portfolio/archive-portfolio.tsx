import { Button, Group, Stack, TextInput } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { type FormEvent, useState } from 'react';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { usePortfolioName } from '../../hooks/use-portfolio-name';

export function ArchivePortfolioModal({ opened, close, portfolioId }: { opened: boolean; close: () => void; portfolioId: string }) {
  return (
    <ResponsiveOverlay opened={opened} onClose={close} title="Archive portfolio">
      <ArchiveForm close={close} portfolioId={portfolioId} />
    </ResponsiveOverlay>
  );
}

function ArchiveForm({ close, portfolioId }: { close: () => void; portfolioId: string }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const name = usePortfolioName(portfolioId);
  const [text, setText] = useState('');
  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/archive', {
    onSuccess: async () => {
      close();
      await navigate({ to: '/dashboard' });
      void client.invalidateQueries();
    }
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (text !== 'ARCHIVE') return;
    mutation.mutate({ params: { path: { portfolioId: Number(portfolioId) } } });
  }

  return (
    <form onSubmit={submit}>
      <Stack>
        <p>Archiving {name || 'this portfolio'} hides it from active portfolios and keeps its trades.</p>
        <TextInput
          label="Type ARCHIVE to confirm"
          value={text}
          onChange={(event) => setText(event.currentTarget.value)}
          readOnly={mutation.isPending}
        />
        {mutation.isError && <p role="alert">Could not archive the portfolio. Try again.</p>}
        <Group justify="flex-end">
          <Button variant="default" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" color="loss" disabled={text !== 'ARCHIVE'} loading={mutation.isPending}>
            Archive portfolio
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
