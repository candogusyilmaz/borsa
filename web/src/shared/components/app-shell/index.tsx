import { ActionIcon, Burger, Group, AppShell as MantineAppShell, NavLink, Text, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { ChartLineUpIcon, HouseIcon, SignOutIcon } from '@phosphor-icons/react';
import { Link, useNavigate, useRouterState } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { useAuth } from '@/shared/hooks/use-auth';
import classes from './app-shell.module.css';

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const [opened, { toggle, close }] = useDisclosure();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const routerState = useRouterState();
  const currentPath = routerState.location.pathname;

  async function handleLogout() {
    await logout();
    await navigate({ to: '/login' });
  }

  return (
    <>
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      <MantineAppShell
        header={{ height: 60 }}
        navbar={{
          width: 240,
          breakpoint: 'sm',
          collapsed: { mobile: !opened }
        }}
        padding="md">
        <MantineAppShell.Header>
          <div className={classes.header}>
            <Group gap="sm">
              <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
              <Link to="/" className={classes.brand} onClick={close}>
                <ChartLineUpIcon size={24} weight="bold" color="var(--color-brand-600)" />
                <Text fw={700} fz="lg">
                  Stocks
                </Text>
              </Link>
            </Group>

            <div className={classes.headerActions}>
              {user && (
                <Text className={classes.userEmail} title={user.email}>
                  {user.email}
                </Text>
              )}
              <ThemeToggle />
              <Tooltip label="Log out" withArrow position="bottom">
                <ActionIcon variant="subtle" color="gray" size="lg" aria-label="Log out" onClick={handleLogout}>
                  <SignOutIcon size={18} weight="bold" />
                </ActionIcon>
              </Tooltip>
            </div>
          </div>
        </MantineAppShell.Header>

        <MantineAppShell.Navbar p="md">
          <NavLink
            component={Link}
            to="/"
            label="Home"
            leftSection={<HouseIcon size={18} weight="bold" />}
            active={currentPath === '/'}
            onClick={close}
          />
        </MantineAppShell.Navbar>

        <MantineAppShell.Main>
          <div id="main-content" tabIndex={-1} className={classes.mainContent}>
            {children}
          </div>
        </MantineAppShell.Main>
      </MantineAppShell>
    </>
  );
}
