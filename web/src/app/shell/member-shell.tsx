import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Avatar, Button, Menu, Stack } from '@mantine/core';
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
import { LanguageSelect } from '~/shared/i18n/language-select';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import { BottomNav } from './bottom-nav';
import styles from './member-shell.module.css';

export function MemberShell({ children }: { children: ReactNode }) {
  const isMobile = useIsMobile();

  return (
    <div className={styles.shell}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
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
  const { t } = useLingui();
  const pathname = useLocation({ select: (location) => location.pathname });
  const onPortfolio = pathname.startsWith('/portfolios/');
  const account = <AccountMenu isMobile={isMobile} />;
  const addTrade = <AddTrade compact={isMobile} />;

  if (isMobile) {
    const section = onPortfolio
      ? t`Portfolios`
      : pathname.startsWith('/positions')
        ? t`Positions`
        : pathname.startsWith('/trades')
          ? t`Trades`
          : t`Dashboard`;
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
        <Link to="/dashboard" className={styles.brand} aria-label={t`Canverse dashboard`}>
          <BrandLogo />
        </Link>
        <nav className={styles.links} aria-label={t`Main navigation`}>
          <Link to="/dashboard" className={styles.navLink}>
            <Trans>Dashboard</Trans>
          </Link>
          <PortfolioSelector className={styles.navLink} active={onPortfolio}>
            <Trans>Portfolios</Trans>
          </PortfolioSelector>
          <Link to="/positions" className={styles.navLink}>
            <Trans>Positions</Trans>
          </Link>
          <Link to="/trades" className={styles.navLink}>
            <Trans>Trades</Trans>
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
  const { t } = useLingui();
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
        <ActionIcon
          variant="filled"
          aria-label={t({ comment: 'Accessible label of the compact action to record a buy or sell', message: 'Add trade' })}
          onClick={start}>
          <IconPlus size={19} />
        </ActionIcon>
      ) : (
        <Button className={styles.addTrade} radius={8} leftSection={<IconPlus size={15} />} onClick={start}>
          <Trans comment="Primary action to record a new buy or sell transaction">Add Trade</Trans>
        </Button>
      )}
      <ResponsiveOverlay opened={opened} onClose={picker.close} title={t`Choose a portfolio`}>
        {portfolios.isPending && (
          <output>
            <Trans>Loading portfolios…</Trans>
          </output>
        )}
        {portfolios.isError && (
          <p role="alert">
            <Trans>Could not load portfolios.</Trans>
          </p>
        )}
        {portfolios.data?.length === 0 && (
          <p>
            <Trans>Create a portfolio from the Portfolios menu to start trading.</Trans>
          </p>
        )}
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

function AccountMenu({ isMobile }: { isMobile: boolean }) {
  const { t } = useLingui();
  const { user, logout } = useAuthentication();
  const navigate = useNavigate();
  const { colorScheme, toggleTheme } = useThemeToggle();
  const clearData = useClearMyDataModal((state) => state.open);
  const isDark = colorScheme === 'dark';

  const [opened, drawer] = useDisclosure();
  const themeLabel = isDark ? t`Switch to light mode` : t`Switch to dark mode`;
  const themeIcon = isDark ? <IconSun size={16} /> : <IconMoon size={16} />;
  const name = user?.name || t`My account`;
  const avatar = <Avatar name={user?.name} size={32} radius="xl" classNames={{ placeholder: styles.avatar }} />;
  const signOut = () => void logout().then(() => navigate({ to: '/login' }));

  if (isMobile) {
    return (
      <>
        <ActionIcon size={36} variant="transparent" aria-label={t`Account menu`} aria-haspopup="dialog" onClick={drawer.open}>
          {avatar}
        </ActionIcon>
        <ResponsiveOverlay opened={opened} onClose={drawer.close} title={name}>
          <Stack gap="xs">
            <LanguageSelect showLabel />
            <Button justify="start" variant="subtle" onClick={toggleTheme} leftSection={themeIcon}>
              {themeLabel}
            </Button>
            <Button
              justify="start"
              variant="subtle"
              leftSection={<IconTrash size={16} />}
              onClick={() => {
                drawer.close();
                clearData();
              }}>
              <Trans>Clear my data</Trans>
            </Button>
            <Button justify="start" variant="subtle" color="loss" leftSection={<IconLogout size={16} />} onClick={signOut}>
              <Trans>Sign out</Trans>
            </Button>
          </Stack>
        </ResponsiveOverlay>
      </>
    );
  }

  return (
    <Menu position="bottom-end" width={230}>
      <Menu.Target>
        <ActionIcon size={36} variant="transparent" aria-label={t`Account menu`}>
          {avatar}
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label className={styles.accountLabel}>{name}</Menu.Label>
        <div className={styles.language}>
          <LanguageSelect showLabel />
        </div>
        <Menu.Item onClick={toggleTheme} leftSection={themeIcon}>
          {themeLabel}
        </Menu.Item>
        <Menu.Item onClick={clearData} leftSection={<IconTrash size={16} />}>
          <Trans>Clear my data</Trans>
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item color="loss" leftSection={<IconLogout size={16} />} onClick={signOut}>
          <Trans>Sign out</Trans>
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
