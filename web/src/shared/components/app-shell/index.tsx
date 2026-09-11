import { ActionIcon, Burger, Group, AppShell as MantineAppShell, NavLink, Text, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { HouseIcon, SignOutIcon } from '@phosphor-icons/react';
import { Link, useNavigate, useRouterState } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { useAuth } from '@/shared/hooks/use-auth';
import type { User } from '@/shared/types/auth';
import classes from './app-shell.module.css';

interface AppShellProps {
  children: ReactNode;
  user: User;
}

export function AppShell({ children, user }: AppShellProps) {
  const [opened, { toggle, close }] = useDisclosure();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const routerState = useRouterState();
  const currentPath = routerState.location.pathname;

  async function handleLogout() {
    await logout();
    await navigate({ to: '/login', replace: true });
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
              <Link to="/app" className={classes.brand} onClick={close}>
                <BrandLogo variant="full" size="md" />
              </Link>
            </Group>

            <div className={classes.headerActions}>
              <Text className={classes.userEmail} title={user.email}>
                {user.email}
              </Text>
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
            to="/app"
            label="Home"
            leftSection={<HouseIcon size={18} weight="bold" />}
            active={currentPath === '/app'}
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
