import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Group, Stack, TextInput } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { type FormEvent, useState } from 'react';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { usePortfolioName } from '../../hooks/use-portfolio-name';

export function ArchivePortfolioModal({ opened, close, portfolioId }: { opened: boolean; close: () => void; portfolioId: string }) {
  const { t } = useLingui();
  return (
    <ResponsiveOverlay opened={opened} onClose={close} title={t`Archive portfolio`}>
      <ArchiveForm close={close} portfolioId={portfolioId} />
    </ResponsiveOverlay>
  );
}

function ArchiveForm({ close, portfolioId }: { close: () => void; portfolioId: string }) {
  const { t } = useLingui();
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
        <p>
          {name ? (
            <Trans>Archiving {name} hides it from active portfolios and keeps its trades.</Trans>
          ) : (
            <Trans>Archiving this portfolio hides it from active portfolios and keeps its trades.</Trans>
          )}
        </p>
        <TextInput
          label={t({ comment: 'ARCHIVE is a literal confirmation code. Keep that word unchanged.', message: 'Type ARCHIVE to confirm' })}
          value={text}
          onChange={(event) => setText(event.currentTarget.value)}
          readOnly={mutation.isPending}
        />
        {mutation.isError && (
          <p role="alert">
            <Trans>Could not archive the portfolio. Try again.</Trans>
          </p>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={close}>
            <Trans>Cancel</Trans>
          </Button>
          <Button type="submit" color="loss" disabled={text !== 'ARCHIVE'} loading={mutation.isPending}>
            <Trans>Archive portfolio</Trans>
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
