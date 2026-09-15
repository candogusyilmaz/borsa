import { Avatar, Badge, Button, Divider, Group, Stack, Text } from '@mantine/core';
import { BankIcon, DevicesIcon, SignOutIcon } from '@phosphor-icons/react';
import { Link, useNavigate } from '@tanstack/react-router';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { useAuth } from '@/shared/hooks/use-auth';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import type { User } from '@/shared/types/auth';
import classes from './app-shell.module.css';

interface AccountMenuOverlayProps {
  user: User;
  activeSessionsCount?: number;
}

function AccountMenu({ user, activeSessionsCount }: AccountMenuOverlayProps) {
  const current = useCurrentOverlay();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const initial = user.email ? user.email.charAt(0).toUpperCase() : 'U';

  async function handleLogout() {
    current.close('logout');
    await logout();
    await navigate({ to: '/login', replace: true });
  }

  return (
    <Stack gap="md">
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

      <Button
        component={Link}
        to="/app/accounts"
        variant="default"
        size="md"
        fullWidth
        leftSection={<BankIcon size={18} weight="bold" />}
        onClick={() => current.close('navigation')}>
        Financial Accounts
      </Button>

      <Button
        component={Link}
        to="/app/sessions"
        variant="default"
        size="md"
        fullWidth
        leftSection={<DevicesIcon size={18} weight="bold" />}
        onClick={() => current.close('navigation')}
        rightSection={
          activeSessionsCount !== undefined ? (
            <Badge color="teal" variant="light" size="xs">
              {activeSessionsCount} active
            </Badge>
          ) : null
        }>
        Sessions
      </Button>

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

      <Button color="red" variant="light" size="md" fullWidth leftSection={<SignOutIcon size={18} weight="bold" />} onClick={handleLogout}>
        Sign Out
      </Button>
    </Stack>
  );
}

export const AccountMenuOverlay = registerOverlay(AccountMenu, {
  name: 'account-menu',
  title: <BrandLogo variant="full" size="sm" />,
  presentation: 'drawer',
  desktopSize: '320px'
});
