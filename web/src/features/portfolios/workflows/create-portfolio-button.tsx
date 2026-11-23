import { Button, Group, Stack, TextInput } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { alerts } from '~/shared/feedback/alerts';

export function CreatePortfolioModal({ opened, close }: { opened: boolean; close: () => void }) {
  const client = useQueryClient();
  const mutation = $api.useMutation('post', '/api/portfolios', {
    onSuccess: () => {
      close();
      alerts.success('Portfolio created successfully');
      void client.invalidateQueries();
    }
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = new FormData(event.currentTarget).get('name');
    if (typeof name !== 'string' || !name.trim()) return;
    mutation.mutate({ body: { name } });
  }

  return (
    <ResponsiveOverlay opened={opened} onClose={close} title="Create portfolio">
      <form onSubmit={submit}>
        <Stack>
          <TextInput
            name="name"
            label="Portfolio name"
            placeholder="e.g. Tech Fund"
            required
            data-autofocus
            readOnly={mutation.isPending}
          />
          {mutation.isError && <p role="alert">Could not create the portfolio. Try again.</p>}
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Cancel
            </Button>
            <Button type="submit" loading={mutation.isPending}>
              Create portfolio
            </Button>
          </Group>
        </Stack>
      </form>
    </ResponsiveOverlay>
  );
}
