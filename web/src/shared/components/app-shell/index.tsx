import { Avatar, Badge, Button, Divider, Drawer, Group, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { HouseIcon, SignOutIcon } from '@phosphor-icons/react';
import { Link, useNavigate } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import { useAuth } from '@/shared/hooks/use-auth';
import type { User } from '@/shared/types/auth';
import classes from './app-shell.module.css';

interface AppShellProps {
  children: ReactNode;
  user: User;
}

export function AppShell({ children, user }: AppShellProps) {
  const [drawerOpened, { open: openDrawer, close: closeDrawer }] = useDisclosure(false);
  const { logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    closeDrawer();
    await logout();
    await navigate({ to: '/login', replace: true });
  }

  const initial = user.email ? user.email.charAt(0).toUpperCase() : 'U';

  return (
    <div className={classes.shell}>
      {/* Accessible Skip Link */}
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      {/* 1. Header */}
      <header className={classes.header}>
        <div className={classes.headerInner}>
          <Link to="/app" className={classes.brandLink} aria-label={`${siteConfig.name} Home`}>
            <BrandLogo variant="full" size="sm" />
          </Link>

          {/* Header Right Actions */}
          <div className={classes.headerActions}>
            <Badge variant="dot" color="teal" size="sm" className={classes.liveBadge}>
              Markets Live
            </Badge>

            <ThemeToggle />

            {/* User Avatar Button -> Opens Drawer */}
            <UnstyledButton
              className={classes.avatarButton}
              onClick={openDrawer}
              aria-label="Account and Settings Menu"
              aria-expanded={drawerOpened}
              aria-haspopup="dialog">
              <Avatar size={34} radius="xl" color="brand">
                {initial}
              </Avatar>
            </UnstyledButton>
          </div>
        </div>
      </header>

      {/* 2. Main Application Content */}
      <main id="main-content" tabIndex={-1} className={classes.main}>
        <div className={classes.container}>{children}</div>
      </main>

      {/* 3. Responsive Account & Settings Drawer */}
      <Drawer
        opened={drawerOpened}
        onClose={closeDrawer}
        position="right"
        size="320px"
        title={<BrandLogo variant="full" size="sm" />}
        classNames={{ content: classes.drawerContent, body: classes.drawerBody }}>
        <Stack gap="md">
          {/* User profile card */}
          <div className={classes.drawerCard}>
            <Group gap="sm">
              <Avatar size={44} radius="xl" color="brand">
                {initial}
              </Avatar>
              <div className={classes.drawerUserMeta}>
                <Text size="sm" fw={600} truncate>
                  {user.email || 'User'}
                </Text>
                <Text size="xs" c="dimmed">
                  User ID: {user.id ? `${user.id.slice(0, 12)}...` : 'N/A'}
                </Text>
              </div>
            </Group>

            <Divider my="sm" />

            <Group justify="space-between">
              <Text size="xs" c="dimmed">
                Session Status
              </Text>
              <Badge color="teal" variant="light" size="sm">
                Authenticated
              </Badge>
            </Group>
          </div>

          {/* Quick Navigation link */}
          <Button
            component={Link}
            to="/"
            variant="default"
            size="md"
            fullWidth
            leftSection={<HouseIcon size={18} weight="bold" />}
            onClick={closeDrawer}>
            Return to Landing Page
          </Button>

          {/* Theme setting row */}
          <Group justify="space-between" className={classes.drawerCard}>
            <div>
              <Text size="sm" fw={500}>
                Appearance
              </Text>
              <Text size="xs" c="dimmed">
                Toggle light / dark mode
              </Text>
            </div>
            <ThemeToggle />
          </Group>

          {/* Log out action */}
          <Button
            color="red"
            variant="light"
            size="md"
            fullWidth
            leftSection={<SignOutIcon size={18} weight="bold" />}
            onClick={handleLogout}>
            Sign Out
          </Button>
        </Stack>
      </Drawer>
    </div>
  );
}
