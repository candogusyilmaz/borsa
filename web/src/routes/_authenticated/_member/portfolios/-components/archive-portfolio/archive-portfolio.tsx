import { ActionIcon, Alert, Button, Group, Modal, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconAlertTriangle, IconArchive, IconChevronRight, IconX } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { $api } from '~/api/openapi';
import { usePortfolioName } from '~/hooks/use-portfolio-name';

export function ArchivePortfolioButton({ portfolioId }: { portfolioId: string }) {
  const [opened, { toggle }] = useDisclosure(false);

  return (
    <>
      <ActionIcon variant="subtle" c="dimmed" onClick={toggle}>
        <IconArchive size={18} />
      </ActionIcon>
      <ArchivePortfolioModal opened={opened} close={toggle} portfolioId={portfolioId} />
    </>
  );
}

type ArchivePortfolioModalProps = {
  opened: boolean;
  close: () => void;
  portfolioId: string;
};

function ArchivePortfolioModal({ opened, close, portfolioId }: ArchivePortfolioModalProps) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const portfolioName = usePortfolioName(portfolioId);

  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/archive', {
    onSuccess: async () => {
      close();
      await navigate({ to: '/dashboard' });
      client.invalidateQueries();
    }
  });

  const [text, setText] = useState('');

  useEffect(() => {
    if (opened) setText('');
  }, [opened]);

  const handleMutate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    mutation.mutate({ params: { path: { portfolioId: Number(portfolioId) } } });
  };

  return (
    <Modal
      centered
      size={485}
      opened={opened}
      onClose={close}
      withCloseButton={false}
      transitionProps={{ transition: 'fade' }}
      classNames={{
        content: 'no-scrollbar'
      }}
      styles={{
        content: {
          padding: '1.5rem'
        }
      }}>
      <Group mb="lg">
        <ThemeIcon radius="lg" variant="light" size="xl" color="red.6">
          <IconArchive />
        </ThemeIcon>
        <ActionIcon onClick={close} variant="subtle" ml="auto" color="gray">
          <IconX size={18} />
        </ActionIcon>
      </Group>
      <Stack gap={2}>
        <Title order={3} fz={24} fw={800} c="var(--text-main)">
          Archive Portfolio?
        </Title>
        <Text size="xs" c="dimmed" fw={600} lts={0.2} lh={1.7}>
          You are about to archive{' '}
          <Text span inherit fw={700} c="var(--text-main)">
            {portfolioName}
          </Text>
          . Archiving a portfolio will hide it from your active portfolios list.
        </Text>
      </Stack>

      <Alert mt={20} variant="light" fw={800} lts={0.5} color="orange.8" title="Important Note" tt="uppercase" icon={<IconAlertTriangle />}>
        <Text fz={11} tt="none" lts={0} fw={600} c="orange.5">
          Archiving a portfolio does not delete its underlying data. You can restore archived portfolios from settings.
        </Text>
      </Alert>
      <form onSubmit={handleMutate}>
        <Stack mt={30}>
          <Stack gap={10}>
            <Text fz={12} fw={600} lts={0.3} c="dimmed">
              TYPE
              <Text inherit span fw={700}>
                {' '}
                ARCHIVE{' '}
              </Text>
              TO CONFIRM
            </Text>
            <TextInput
              name="name"
              placeholder="Confirm portfolio archiving"
              readOnly={mutation.isPending}
              value={text}
              onChange={(e) => setText(e.currentTarget.value)}
              styles={{
                input: {
                  paddingLeft: 20,
                  height: 50,
                  fontSize: 16,
                  fontWeight: 600
                }
              }}
            />
          </Stack>
          <Group mt="lg">
            <Button flex={1} h={50} variant="default" onClick={close}>
              Cancel
            </Button>
            <Button
              flex={2}
              type="submit"
              loading={mutation.isPending}
              disabled={text !== 'ARCHIVE'}
              h={50}
              color="red.7"
              rightSection={<IconChevronRight size={16} />}>
              ARCHIVE PORTFOLIO
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
