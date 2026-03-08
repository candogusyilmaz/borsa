import { Drawer, useMantineColorScheme } from '@mantine/core';
import {
  IconArrowsLeftRight,
  IconFolderOpen,
  IconHome,
  IconListDetails,
  IconLogout,
  IconMoon,
  IconSun,
  IconUser
} from '@tabler/icons-react';
import { Link, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { useAuthentication } from '~/lib/AuthenticationContext';
import classes from './bottom-nav.module.css';
import { CreatePortfolioButton } from './create-portfolio-button';

const NAV_TABS = [
  {
    id: 'dashboard',
    label: 'Home',
    icon: <IconHome size={18} strokeWidth={1.75} />,
    to: '/dashboard'
  },
  {
    id: 'positions',
    label: 'Positions',
    icon: <IconListDetails size={18} strokeWidth={1.75} />,
    to: '/positions'
  },
  {
    id: 'trades',
    label: 'Trades',
    icon: <IconArrowsLeftRight size={18} strokeWidth={1.75} />,
    to: '/trades'
  }
] as const;

export function BottomNav() {
  const [portfolioSheetOpen, setPortfolioSheetOpen] = useState(false);
  const [profileSheetOpen, setProfileSheetOpen] = useState(false);

  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const { logout } = useAuthentication();
  const navigate = useNavigate();

  const { data: portfolios } = $api.useQuery('get', '/api/portfolios', {});

  const toggleTheme = () => {
    if (!document.startViewTransition) {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
      return;
    }
    document.startViewTransition(() => {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
    });
  };

  return (
    <>
      <nav className={classes.nav}>
        <div className={classes.inner}>
          {/* Route-based tabs */}
          {NAV_TABS.map((tab) => (
            <Link key={tab.id} to={tab.to} className={classes.tab}>
              {({ isActive }) => (
                <>
                  {isActive && <span className={classes.pip} />}
                  <span className={`${classes.iconBox}${isActive ? ` ${classes.iconBoxActive}` : ''}`}>{tab.icon}</span>
                  <span className={`${classes.label}${isActive ? ` ${classes.labelActive}` : ''}`}>{tab.label}</span>
                </>
              )}
            </Link>
          ))}

          {/* Portfolios — opens bottom sheet */}
          <button type="button" className={classes.tab} onClick={() => setPortfolioSheetOpen(true)}>
            <span className={classes.iconBox}>
              <IconFolderOpen size={18} strokeWidth={1.75} />
            </span>
            <span className={classes.label}>Portfolios</span>
          </button>

          {/* Profile — opens profile sheet */}
          <button type="button" className={classes.tab} onClick={() => setProfileSheetOpen(true)}>
            <span className={`${classes.avatar}${profileSheetOpen ? ` ${classes.avatarActive}` : ''}`}>
              <IconUser size={15} strokeWidth={2} />
            </span>
            <span className={`${classes.label}${profileSheetOpen ? ` ${classes.labelActive}` : ''}`}>Profile</span>
          </button>
        </div>
      </nav>

      {/* ── Portfolio Sheet ──────────────────────────────── */}
      <Drawer.Root
        opened={portfolioSheetOpen}
        onClose={() => setPortfolioSheetOpen(false)}
        radius="md"
        position="bottom"
        size="80%"
        offset={8}>
        <Drawer.Overlay />
        <Drawer.Content>
          <div className={classes.sheetHeader}>
            <p className={classes.sheetSectionLabel}>My Portfolios</p>
            <CreatePortfolioButton />
          </div>
          <div className={classes.sheetBody}>
            {portfolios?.map((portfolio) => (
              <button
                key={portfolio.id}
                type="button"
                className={classes.portfolioItem}
                onClick={() => {
                  navigate({ to: '/portfolios/$portfolioId', params: { portfolioId: portfolio.id } });
                  setPortfolioSheetOpen(false);
                }}>
                <span className={classes.portfolioIconBox}>
                  <IconFolderOpen size={18} strokeWidth={1.75} />
                </span>
                <p className={classes.portfolioName}>{portfolio.name}</p>
              </button>
            ))}
          </div>
        </Drawer.Content>
      </Drawer.Root>

      {/* ── Profile Sheet ─────────────────────────────────── */}
      <Drawer.Root opened={profileSheetOpen} onClose={() => setProfileSheetOpen(false)} radius="md" position="bottom" size={350} offset={8}>
        <Drawer.Overlay />
        <Drawer.Content>
          <div className={classes.profileHero}>
            <div className={classes.profileAvatar}>
              <IconUser size={20} strokeWidth={2} />
            </div>
            <div>
              <p style={{ margin: 0, fontSize: 15, fontWeight: 800, color: 'var(--cv-text-main)' }}>My Account</p>
              <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--cv-text-muted)' }}>Manage your settings</p>
            </div>
          </div>
          <div className={classes.sheetBody}>
            {/* Theme toggle */}
            <button
              type="button"
              className={classes.profileRow}
              onClick={() => {
                toggleTheme();
                setProfileSheetOpen(false);
              }}>
              <span
                className={classes.profileRowIconBox}
                style={{
                  background: colorScheme === 'dark' ? 'rgba(250,204,21,0.12)' : 'rgba(71,85,105,0.1)',
                  color: colorScheme === 'dark' ? '#facc15' : 'var(--cv-text-muted)'
                }}>
                {colorScheme === 'dark' ? <IconSun size={17} strokeWidth={1.75} /> : <IconMoon size={17} strokeWidth={1.75} />}
              </span>
              <div>
                <p className={classes.profileRowLabel}>{colorScheme === 'dark' ? 'Switch to Light' : 'Switch to Dark'}</p>
                <p className={classes.profileRowSub}>Toggle appearance</p>
              </div>
            </button>

            {/* Sign out */}
            <button
              type="button"
              className={classes.profileRow}
              onClick={() => {
                setProfileSheetOpen(false);
                logout().then(() => navigate({ to: '/login' }));
              }}>
              <span className={classes.profileRowIconBox} style={{ background: 'var(--cv-loss-muted)', color: 'var(--cv-loss)' }}>
                <IconLogout size={17} strokeWidth={1.75} />
              </span>
              <div>
                <p className={`${classes.profileRowLabel} ${classes.profileRowDangerLabel}`}>Sign Out</p>
                <p className={classes.profileRowSub}>See you next time</p>
              </div>
            </button>
          </div>
        </Drawer.Content>
      </Drawer.Root>
    </>
  );
}
