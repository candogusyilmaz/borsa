import { ActionIcon, Avatar, Button, Menu } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconLogout, IconMoon, IconPlus, IconSun, IconTrash } from '@tabler/icons-react';
import { Link, useLocation, useNavigate, useParams } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { $api } from '~/api/openapi';
import { useClearMyDataModal } from '~/features/account/workflows/clear-my-data';
import { useAuthentication } from '~/features/auth/authentication-context';
import { PortfolioSelector } from '~/features/portfolios';
import { useTransactionModalStore } from '~/features/trades';
import { BrandLogo } from '~/shared/components/brand-logo';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import { BottomNav } from './bottom-nav';
import styles from './member-shell.module.css';

export function MemberShell({ children }: { children: ReactNode }) {
  const isMobile = useIsMobile();

  return (
    <div className={styles.shell}>
      <a href="#main-content" className={styles.skipLink}>
        Skip to main content
      </a>
      <ShellNavigation isMobile={isMobile} />
      <main id="main-content" tabIndex={-1} className={styles.main}>
        {children}
      </main>
      {isMobile && <BottomNav />}
    </div>
  );
}

function ShellNavigation({ isMobile }: { isMobile: boolean }) {
  const pathname = useLocation({ select: (location) => location.pathname });
  const onPortfolio = pathname.startsWith('/portfolios/');
  const account = <AccountMenu />;
  const addTrade = <AddTrade compact={isMobile} />;

  if (isMobile) {
    const section = onPortfolio
      ? 'Portfolios'
      : pathname.startsWith('/positions')
        ? 'Positions'
        : pathname.startsWith('/trades')
          ? 'Trades'
          : 'Dashboard';
    return (
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <span className={styles.mobileTitle}>{section}</span>
          <div className={styles.actions}>
            {addTrade}
            {account}
          </div>
        </div>
      </header>
    );
  }

  return (
    <header className={styles.header}>
      <div className={styles.headerInner}>
        <Link to="/dashboard" className={styles.brand} aria-label="Canverse dashboard">
          <BrandLogo />
        </Link>
        <nav className={styles.links} aria-label="Main navigation">
          <Link to="/dashboard" className={styles.navLink}>
            Dashboard
          </Link>
          <PortfolioSelector className={styles.navLink} active={onPortfolio}>
            Portfolios
          </PortfolioSelector>
          <Link to="/positions" className={styles.navLink}>
            Positions
          </Link>
          <Link to="/trades" className={styles.navLink}>
            Trades
          </Link>
        </nav>
        <div className={styles.actions}>
          {addTrade}
          {account}
        </div>
      </div>
    </header>
  );
}

function AddTrade({ compact }: { compact: boolean }) {
  const { portfolioId } = useParams({ strict: false });
  const open = useTransactionModalStore((state) => state.open);
  const [opened, picker] = useDisclosure();
  const portfolios = $api.useQuery('get', '/api/portfolios', {}, { enabled: opened });

  function addTo(id: string) {
    picker.close();
    open('Buy', undefined, id);
  }

  function start() {
    if (portfolioId) addTo(portfolioId);
    else picker.open();
  }

  return (
    <>
      {compact ? (
        <ActionIcon variant="filled" aria-label="Add trade" onClick={start}>
          <IconPlus size={19} />
        </ActionIcon>
      ) : (
        <Button className={styles.addTrade} radius={8} leftSection={<IconPlus size={15} />} onClick={start}>
          Add Trade
        </Button>
      )}
      <ResponsiveOverlay opened={opened} onClose={picker.close} title="Choose a portfolio">
        {portfolios.isPending && <output>Loading portfolios…</output>}
        {portfolios.isError && <p role="alert">Could not load portfolios.</p>}
        {portfolios.data?.length === 0 && <p>Create a portfolio from the Portfolios menu to start trading.</p>}
        <ul className={styles.portfolioOptions}>
          {portfolios.data?.map(({ id, name }) => (
            <li key={id}>
              <button type="button" onClick={() => addTo(id)}>
                {name}
              </button>
            </li>
          ))}
        </ul>
      </ResponsiveOverlay>
    </>
  );
}

function AccountMenu() {
  const { user, logout } = useAuthentication();
  const navigate = useNavigate();
  const { colorScheme, toggleTheme } = useThemeToggle();
  const clearData = useClearMyDataModal((state) => state.open);
  const isDark = colorScheme === 'dark';

  return (
    <Menu position="bottom-end" width={230}>
      <Menu.Target>
        <ActionIcon size={36} variant="transparent" aria-label="Account menu">
          <Avatar name={user?.name} size={32} radius="xl" classNames={{ placeholder: styles.avatar }} />
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label className={styles.accountLabel}>{user?.name || 'My account'}</Menu.Label>
        <Menu.Item onClick={toggleTheme} leftSection={isDark ? <IconSun size={16} /> : <IconMoon size={16} />}>
          {isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        </Menu.Item>
        <Menu.Item onClick={clearData} leftSection={<IconTrash size={16} />}>
          Clear my data
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item color="loss" leftSection={<IconLogout size={16} />} onClick={() => void logout().then(() => navigate({ to: '/login' }))}>
          Sign out
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
