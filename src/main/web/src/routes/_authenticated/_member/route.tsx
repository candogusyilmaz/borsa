import { AppShell, Avatar, Burger, Flex, Group, Menu, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronDown, IconExchange, IconHome, IconLogout, IconSearch, IconSettings, IconTrash } from '@tabler/icons-react';
import { Link, Outlet, createFileRoute, linkOptions } from '@tanstack/react-router';
import cx from 'clsx';
import { useState } from 'react';
import Logo from '~/components/Logo';
import { useAuthentication } from '~/lib/AuthenticationContext';
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
  const { user, logout } = useAuthentication();
  const [opened, { toggle }] = useDisclosure();

  const [userMenuOpened, setUserMenuOpened] = useState(false);
  const navigate = Route.useNavigate();

  return (
    <AppShell header={{ height: 60 }} navbar={{ width: 275, breakpoint: 'md', collapsed: { mobile: !opened } }} padding="md" layout="alt">
      <AppShell.Header>
        <Flex align="center" h="100%" justify="space-between" px="md">
          <Group>
            <Burger opened={opened} onClick={toggle} hiddenFrom="md" size="sm" />
            <TextInput fz="lg" variant="unstyled" placeholder="Search..." leftSection={<IconSearch size={20} />} />
          </Group>

          <Menu
            width={260}
            position="bottom-end"
            transitionProps={{ transition: 'pop-top-right' }}
            withinPortal
            onClose={() => setUserMenuOpened(false)}
            onOpen={() => setUserMenuOpened(true)}>
            <Menu.Target>
              <UnstyledButton
                className={cx(common.user, {
                  [common.userActive]: userMenuOpened
                })}>
                <Group gap={7}>
                  <Avatar size={24} />
                  <Text fw={500} size="sm" lh={1} mr={3}>
                    {user?.name}
                  </Text>
                  <IconChevronDown size={12} stroke={1.5} />
                </Group>
              </UnstyledButton>
            </Menu.Target>

            <Menu.Dropdown>
              <Menu.Label>Settings</Menu.Label>
              <Menu.Item leftSection={<IconSettings size={16} stroke={1.5} />} disabled>
                Account settings
              </Menu.Item>
              <Menu.Item
                leftSection={<IconLogout size={16} stroke={1.5} />}
                onClick={() => {
                  logout();
                  navigate({ to: '/login' });
                }}>
                Logout
              </Menu.Item>

              <Menu.Divider />

              <Menu.Label>Danger zone</Menu.Label>

              <Menu.Item color="red" leftSection={<IconTrash size={16} stroke={1.5} />}>
                Clear my data
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Flex>
      </AppShell.Header>
      <AppShell.Navbar>
        <Stack>
          <UnstyledButton mx="lg" mt="lg">
            <Logo />
          </UnstyledButton>
          <Stack gap={8} p={0} mx="xs" mt="lg">
            {NAV_LINKS.map((s) => {
              return (
                <Link key={s.options.to} {...s.options} className={common.navLink} onClick={toggle}>
                  <Group align="center">
                    {s.icon}
                    {s.label}
                  </Group>
                </Link>
              );
            })}
          </Stack>
        </Stack>
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
