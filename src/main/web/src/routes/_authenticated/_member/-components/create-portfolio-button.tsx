import { ActionIcon, Button, Group, Modal, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconFolder, IconFolderPlus, IconPlus, IconSparkles, IconX } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { queries } from '~/api';
import { $api } from '~/api/openapi';
import { alerts } from '~/lib/alert';
import classes from './create-portfolio-button.module.css';

export function CreatePortfolioButton() {
  const [opened, { toggle }] = useDisclosure(false);

  return (
    <>
      <ActionIcon variant="subtle" size="xs" color="var(--text-muted)" style={{ borderRadius: '6px' }} onClick={toggle}>
        <IconPlus />
      </ActionIcon>
      <CreatePortfolioModal opened={opened} close={toggle} />
    </>
  );
}

const COLORS = [
  { name: 'Blue', hex: '#3b82f6' },
  { name: 'Purple', hex: '#a855f7' },
  { name: 'Emerald', hex: '#10b981' },
  { name: 'Amber', hex: '#f59e0b' },
  { name: 'Rose', hex: '#f43f5e' },
  { name: 'Slate', hex: '#475569' }
] as const;

type CreatePortfolioModalProps = {
  opened: boolean;
  close: () => void;
};

function CreatePortfolioModal({ opened, close }: CreatePortfolioModalProps) {
  const client = useQueryClient();
  const mutation = $api.useMutation('post', '/api/portfolios', {
    onSuccess: () => {
      close();
      alerts.success('Portfolio created successfully');
      client.invalidateQueries({ queryKey: queries.portfolio.fetchPortfolios().queryKey });
    }
  });

  const [color, setColor] = useState<(typeof COLORS)[number]['hex']>(COLORS[0].hex);

  const handleMutate = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const name = formData.get('name') as string;

    if (!name) {
      alerts.error('Portfolio name is required');
      return;
    }

    mutation.mutate({ body: { name } });
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
      <Group mb="xl">
        <ThemeIcon radius="lg" variant="light" size="xl" color="var(--accent-color)">
          <IconFolderPlus />
        </ThemeIcon>
        <Stack gap={0}>
          <Title order={3} size="md" c="var(--text-main)">
            Create your portfolio
          </Title>
          <Text size="sm" c="dimmed">
            Set up your preferences
          </Text>
        </Stack>
        <ActionIcon onClick={close} variant="subtle" ml="auto" color="gray">
          <IconX size={18} />
        </ActionIcon>
      </Group>
      <form onSubmit={handleMutate}>
        <Stack gap={35}>
          <TextInput
            name="name"
            label="PORTFOLIO NAME"
            placeholder="e.g. Tech Fund"
            readOnly={mutation.isPending}
            leftSection={<IconFolder size={18} />}
            leftSectionWidth={50}
            styles={{
              label: {
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--text-muted)',
                marginBottom: 4,
                marginLeft: 2
              },
              input: {
                height: 50,
                fontSize: 16,
                fontWeight: 300
              }
            }}
          />

          <Stack gap={10}>
            <Group justify="space-between">
              <Text fz={12} lts={-0.08} ml={2} fw={600} c="var(--text-muted)">
                THEME COLOR
              </Text>
              <Text fz={10} c="var(--accent-color)" fw={700}>
                Visual Identity
              </Text>
            </Group>
            <Group grow>
              {COLORS.map((s) => {
                const isSelected = color === s.hex;

                return (
                  <button
                    type="button"
                    key={s.hex}
                    onClick={() => setColor(s.hex)}
                    className={classes.colorButton}
                    data-active={isSelected}
                    style={{
                      backgroundColor: s.hex,
                      height: '40px',
                      width: '100%',
                      borderRadius: '12px',
                      transition: 'all 0.2s ease-in-out',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      border: 'none',
                      cursor: 'pointer',
                      boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                      position: 'relative',
                      overflow: 'hidden',
                      padding: 0
                      // Active scale effect is harder to do inline; typically done via CSS or JS state
                    }}>
                    {isSelected && (
                      <div
                        style={{
                          backgroundColor: 'rgba(255, 255, 255, 0.2)',
                          position: 'absolute',
                          inset: 0,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          // Simple animation approximation
                          animation: 'zoomIn 0.2s ease-out'
                        }}>
                        <IconCheck size={18} color="white" strokeWidth={3} />
                      </div>
                    )}
                  </button>
                );
              })}
            </Group>
          </Stack>

          <Group align="center" bg="transparent" bd="1px solid var(--lighter-border-color)" bdrs="lg" p="lg">
            <ThemeIcon c="var(--accent-color)" variant="subtle" size="lg" radius="md">
              <IconSparkles style={{ flexShrink: 0 }} />
            </ThemeIcon>
            <Text flex={1} fz={12} lts={-0.15} c="var(--accent-color)" fw={600}>
              Settings and colors can be adjusted later in your portfolio preferences.
            </Text>
          </Group>

          <Button type="submit" loading={mutation.isPending} h={50} color="var(--accent-color)">
            CREATE PORTFOLIO
          </Button>
        </Stack>
      </form>
    </Modal>
  );
}
