import { ActionIcon, AppShell, Button, Flex, Group, ScrollArea, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconCirclePlusFilled,
  IconExchange,
  IconHome,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand
} from '@tabler/icons-react';
import { createFileRoute, Link, linkOptions, Outlet } from '@tanstack/react-router';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';
import { UserNavbar } from '~/components/UserAvatarMenu';
import common from '~/styles/common.module.css';

export const Route = createFileRoute('/_authenticated/_member')({
  component: RouteComponent
});

const NAV_LINKS = [
  {
    label: 'Portfolio',
    icon: <IconHome size={20} />,
    options: linkOptions({
      to: '/portfolio'
    })
  },
  {
    label: 'Stocks',
    icon: <IconExchange size={20} />,
    options: linkOptions({
      to: '/stocks'
    })
  }
];

function RouteComponent() {
  const [opened, { toggle }] = useDisclosure();
  const [mobileOpened, { toggle: toggleMobile }] = useDisclosure(true);
  const { open } = useTransactionModalStore();

  return (
    <AppShell
      header={{ height: 50 }}
      navbar={{
        width: 280,
        breakpoint: 'sm',
        collapsed: { mobile: mobileOpened, desktop: opened }
      }}
      layout="alt">
      <AppShell.Header>
        <Flex align="center" h="100%" justify="space-between" px="md">
          <Group>
            <ActionIcon
              variant="subtle"
              color="gray.6"
              onClick={toggle}
              visibleFrom="sm"
              style={{
                height: '26px',
                width: '26px'
              }}>
              {opened ? (
                <IconLayoutSidebarLeftExpand style={{ width: '80%' }} />
              ) : (
                <IconLayoutSidebarLeftCollapse style={{ width: '80%' }} />
              )}
            </ActionIcon>
            <ActionIcon
              style={{
                height: '26px',
                width: '26px'
              }}
              variant="transparent"
              color="gray.6"
              onClick={toggleMobile}
              hiddenFrom="sm">
              <IconLayoutSidebarLeftExpand style={{ width: '80%' }} />
            </ActionIcon>
          </Group>
        </Flex>
      </AppShell.Header>
      <AppShell.Navbar bg="dark.8">
        <AppShell.Section>
          <Group h={60} align="center" mx="md" pt="sm" pb={3}>
            <UnstyledButton h="95%">
              <Group h="100%">
                <img
                  src="/assets/logo.png"
                  alt="Canverse"
                  style={{
                    maxWidth: '100%',
                    maxHeight: '100%',
                    objectFit: 'contain'
                  }}
                />
                <Text size="xl" fw={500} lts={1.7}>
                  CANVERSE
                </Text>
              </Group>
            </UnstyledButton>
            <ActionIcon
              hiddenFrom="sm"
              style={{
                height: '26px',
                width: '26px'
              }}
              variant="transparent"
              color="gray.6"
              onClick={toggleMobile}
              ml="auto"
              pb={3}>
              <IconLayoutSidebarLeftCollapse style={{ width: '80%' }} />
            </ActionIcon>
          </Group>
        </AppShell.Section>

        <AppShell.Section grow component={ScrollArea}>
          <Stack gap={8} p={0} mx="sm" mt="lg">
            <Text c="dimmed" fw={600} fz={12}>
              Quick Actions
            </Text>
            <Button
              justify={'left'}
              size="compact-xs"
              variant="subtle"
              color="gray"
              leftSection={<IconCirclePlusFilled size={16} color="teal" />}
              onClick={() => open('Buy')}>
              Buy Stock
            </Button>
            <Button
              justify={'left'}
              size="compact-xs"
              variant="subtle"
              color="gray"
              leftSection={<IconCirclePlusFilled size={16} color="red" />}
              onClick={() => open('Sell')}>
              Sell Stock
            </Button>
          </Stack>
          <Stack gap={8} p={0} mx="sm" mt="lg">
            <Text c="dimmed" fw={600} fz={12}>
              Menu
            </Text>
            {NAV_LINKS.map((s) => {
              return (
                <Link key={s.options.to} {...s.options} className={common.navLink}>
                  <Group align="center">
                    {s.icon}
                    {s.label}
                  </Group>
                </Link>
              );
            })}
          </Stack>
        </AppShell.Section>

        <AppShell.Section px="xs" py="sm">
          <UserNavbar />
        </AppShell.Section>
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
