import { ActionIcon, Button, Drawer, Group, Loader, NavLink, Stack, Text } from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import { IconArrowsLeftRight, IconFolderOpen, IconHome, IconListDetails, IconLogout, IconMoon, IconSun } from '@tabler/icons-react';
import { Link, linkOptions, useNavigate } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { $api } from '~/api/openapi';
import { useAuthentication } from '~/features/auth/authentication-context';
import { CreatePortfolioButton } from '~/features/portfolios';
import { BrandLogo } from '~/shared/components/brand-logo';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import { BottomNav } from './bottom-nav';
import styles from './member-shell.module.css';

const navigation = [
  { label: 'Dashboard', icon: IconHome, options: linkOptions({ to: '/dashboard' }) },
  { label: 'Positions', icon: IconListDetails, options: linkOptions({ to: '/positions' }) },
  { label: 'Trades', icon: IconArrowsLeftRight, options: linkOptions({ to: '/trades' }) }
];

export function MemberShell({ children }: { children: ReactNode }) {
  const isMobile = useMediaQuery('(max-width: 63.999em)');
  const { logout } = useAuthentication();
  const navigate = useNavigate();
  const { colorScheme, toggleTheme } = useThemeToggle();
  const themeLabel = colorScheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
  const [portfoliosOpened, portfoliosDrawer] = useDisclosure();

  function signOut() {
    logout();
    void navigate({ to: '/login' });
  }

  return (
    <div className={styles.shell}>
      <a href="#main-content" className={styles.skipLink}>
        Skip to main content
      </a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/dashboard" className={styles.brand} aria-label="Canverse dashboard">
            <BrandLogo />
          </Link>
          <Group component="nav" aria-label="Main navigation" gap="xs" visibleFrom="md" className={styles.links}>
            {navigation.map(({ label, icon: Icon, options }) => (
              <Button key={options.to} component={Link} {...options} variant="subtle" size="sm" leftSection={<Icon size={17} />}>
                {label}
              </Button>
            ))}
            <Button
              variant="subtle"
              size="sm"
              leftSection={<IconFolderOpen size={17} />}
              onClick={portfoliosDrawer.open}
              aria-haspopup="dialog">
              Portfolios
            </Button>
          </Group>
          <Group gap={4} visibleFrom="md">
            <ActionIcon onClick={toggleTheme} aria-label={themeLabel} title={themeLabel}>
              {colorScheme === 'dark' ? <IconSun size={20} /> : <IconMoon size={20} />}
            </ActionIcon>
            <ActionIcon onClick={signOut} color="red" aria-label="Sign out" title="Sign out">
              <IconLogout size={20} />
            </ActionIcon>
          </Group>
        </div>
      </header>
      <main id="main-content" tabIndex={-1} className={styles.main}>
        {children}
      </main>
      {isMobile ? (
        <BottomNav />
      ) : (
        <Drawer title="Portfolios" opened={portfoliosOpened} onClose={portfoliosDrawer.close} position="right">
          <PortfolioNavigation close={portfoliosDrawer.close} />
        </Drawer>
      )}
    </div>
  );
}

function PortfolioNavigation({ close }: { close: () => void }) {
  const portfolios = $api.useQuery('get', '/api/portfolios', {});

  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text fw={600}>My portfolios</Text>
        <CreatePortfolioButton />
      </Group>
      {portfolios.isPending && <Loader size="sm" aria-label="Loading portfolios" />}
      {portfolios.isError && (
        <Text c="dimmed" size="sm">
          Could not load portfolios.
        </Text>
      )}
      {portfolios.data?.length === 0 && (
        <Text c="dimmed" size="sm">
          No portfolios yet.
        </Text>
      )}
      {portfolios.data?.map(({ id, name }) => (
        <NavLink
          key={id}
          label={name}
          leftSection={<IconFolderOpen size={18} />}
          renderRoot={(props) => <Link {...props} to="/portfolios/$portfolioId" params={{ portfolioId: id }} onClick={close} />}
        />
      ))}
    </Stack>
  );
}
