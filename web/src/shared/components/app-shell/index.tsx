import { Avatar, Badge, Button, UnstyledButton } from '@mantine/core';
import { BankIcon, HouseIcon, StackIcon, TrendUpIcon, UserIcon } from '@phosphor-icons/react';
import { Link, useLocation } from '@tanstack/react-router';
import { type MouseEvent, type ReactNode, useMemo, useRef, useState } from 'react';
import { $api } from '@/api/client';
import { BrandLogo } from '@/shared/components/brand-logo';
import { type BottomNavItem, MobileBottomNav } from '@/shared/components/mobile-bottom-nav';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import type { OverlayHandle } from '@/shared/overlay';
import type { User } from '@/shared/types/auth';
import { AccountMenuOverlay } from './account-menu';
import classes from './app-shell.module.css';

interface AppShellProps {
  children: ReactNode;
  user: User;
}

export function AppShell({ children, user }: AppShellProps) {
  const pathname = useLocation({
    select: (location) => location.pathname
  });

  const sessionsQuery = $api.useQuery('get', '/api/v1/auth/sessions');
  const activeSessionsCount = sessionsQuery.data ? sessionsQuery.data.filter((s) => s.status === 'ACTIVE').length : undefined;
  const [accountMenuOpened, setAccountMenuOpened] = useState(false);
  const accountMenuHandle = useRef<OverlayHandle | null>(null);

  const activeId = useMemo(() => {
    if (pathname === '/app/accounts' || pathname.startsWith('/app/accounts/')) {
      return 'accounts';
    }
    if (pathname === '/app/investing' || pathname.startsWith('/app/investing/')) {
      return 'investing';
    }
    if (pathname === '/app/instruments' || pathname.startsWith('/app/instruments/')) {
      return 'instruments';
    }
    if (pathname === '/app/sessions' || pathname.startsWith('/app/sessions/')) {
      return 'sessions';
    }
    if (pathname === '/app' || pathname === '/app/') {
      return 'dashboard';
    }
    return undefined;
  }, [pathname]);

  const navItems = useMemo<readonly BottomNavItem[]>(
    () => [
      {
        id: 'dashboard',
        label: 'Dashboard',
        to: '/app',
        icon: ({ active }) => <HouseIcon size={22} weight={active ? 'fill' : 'bold'} />
      },
      {
        id: 'accounts',
        label: 'Accounts',
        to: '/app/accounts',
        icon: ({ active }) => <BankIcon size={22} weight={active ? 'fill' : 'bold'} />
      },
      {
        id: 'investing',
        label: 'Investing',
        to: '/app/investing',
        icon: ({ active }) => <TrendUpIcon size={22} weight={active ? 'fill' : 'bold'} />
      },
      {
        id: 'menu',
        label: 'Menu',
        to: '#',
        icon: ({ active }) => <UserIcon size={22} weight={active ? 'fill' : 'bold'} />,
        ariaLabel: 'Open account and settings menu'
      }
    ],
    []
  );

  function toggleAccountMenu() {
    if (accountMenuOpened) {
      accountMenuHandle.current?.close('toggle');
      return;
    }

    const handle = AccountMenuOverlay.open({ activeSessionsCount });
    accountMenuHandle.current = handle;
    setAccountMenuOpened(true);
    void handle.closed.then(() => {
      if (accountMenuHandle.current?.id === handle.id) {
        accountMenuHandle.current = null;
        setAccountMenuOpened(false);
      }
    });
  }

  function handleItemSelect(item: BottomNavItem, event: MouseEvent<HTMLAnchorElement>) {
    if (item.id === 'menu') {
      event.preventDefault();
      toggleAccountMenu();
    }
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

          {/* Desktop Nav Links */}
          <nav className={classes.navLinks} aria-label="Main Navigation">
            <Button
              component={Link}
              to="/app"
              activeOptions={{ exact: true }}
              variant="subtle"
              size="sm"
              leftSection={<HouseIcon size={16} weight="bold" />}>
              Dashboard
            </Button>
            <Button
              component={Link}
              to="/app/accounts"
              activeOptions={{ exact: true }}
              variant="subtle"
              size="sm"
              leftSection={<BankIcon size={16} weight="bold" />}>
              Accounts
            </Button>
            <Button
              component={Link}
              to="/app/investing"
              activeOptions={{ exact: true }}
              variant="subtle"
              size="sm"
              leftSection={<TrendUpIcon size={16} weight="bold" />}>
              Investing
            </Button>
            <Button
              component={Link}
              to="/app/instruments"
              activeOptions={{ exact: true }}
              variant="subtle"
              size="sm"
              leftSection={<StackIcon size={16} weight="bold" />}>
              Instruments
            </Button>
          </nav>

          {/* Header Right Actions */}
          <div className={classes.headerActions}>
            <Badge variant="dot" color="teal" size="sm" className={classes.liveBadge}>
              Markets Live
            </Badge>

            <ThemeToggle />

            {/* User Avatar Button -> Opens account menu */}
            <UnstyledButton
              className={classes.avatarButton}
              onClick={toggleAccountMenu}
              aria-label="Account and Settings Menu"
              aria-haspopup="dialog"
              aria-expanded={accountMenuOpened}>
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

      {/* 3. Mobile Bottom Navigation */}
      <MobileBottomNav
        items={navItems}
        activeId={activeId}
        activeIndicator="pill"
        surface="solid"
        position="fixed"
        onItemSelect={handleItemSelect}
        renderLink={({ item, linkProps, children }) => {
          if (item.id === 'menu') {
            return (
              <UnstyledButton component="a" href="#menu" aria-haspopup="dialog" aria-expanded={accountMenuOpened} {...linkProps}>
                {children}
              </UnstyledButton>
            );
          }

          return (
            <Link to={item.to} activeOptions={{ exact: item.to === '/app' }} {...linkProps}>
              {children}
            </Link>
          );
        }}
      />
    </div>
  );
}

export { AccountMenuOverlay } from './account-menu';
