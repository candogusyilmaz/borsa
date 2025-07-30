import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconFolderPlus } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { mutations, queries } from '~/api';
import { alerts } from '~/lib/alert';
import classes from '~/styles/common.module.css';

export function CreatePortfolioButton() {
  const [opened, { toggle }] = useDisclosure(false);

  return (
    <>
      <Button
        onClick={toggle}
        justify="start"
        c="var(--mantine-color-teal-5)"
        className={classes.navLinkPortfolio}
        leftSection={<IconFolderPlus size={20} />}
        variant="subtle">
        Create Portfolio
      </Button>
      <CreatePortfolioModal opened={opened} close={toggle} />
    </>
  );
}

type CreatePortfolioModalProps = {
  opened: boolean;
  close: () => void;
};

function CreatePortfolioModal({ opened, close }: CreatePortfolioModalProps) {
  const client = useQueryClient();
  const mutation = useMutation({
    ...mutations.portfolio.createPortfolio,
    onSuccess: () => {
      close();
      alerts.success('Portfolio created successfully');
      client.invalidateQueries({ queryKey: queries.portfolio.fetchPortfolios().queryKey });
    }
  });

  const handleMutate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const name = formData.get('name') as string;

    if (!name) {
      alerts.error('Portfolio name is required');
      return;
    }

    mutation.mutate({ name });
  };

  return (
    <Modal centered opened={opened} onClose={close} title="Create Portfolio" transitionProps={{ transition: 'fade' }}>
      <form onSubmit={handleMutate}>
        <Stack>
          <TextInput name="name" label="Portfolio Name" placeholder="Enter portfolio name" readOnly={mutation.isPending} />

          <Group gap="xs" justify="flex-end">
            <Button type="button" variant="subtle" color="gray" onClick={close} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button type="submit" color="blue" loading={mutation.isPending}>
              Create Portfolio
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
