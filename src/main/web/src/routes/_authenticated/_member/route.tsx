import { Drawer, useMantineColorScheme } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import {
  IconArrowsLeftRight,
  IconChevronRight,
  IconFolderOpen,
  IconHome,
  IconListDetails,
  IconLogout,
  IconMoon,
  IconSun,
  IconTrendingUp,
  IconZoomPan
} from '@tabler/icons-react';
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link, linkOptions, Outlet, redirect, useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';
import { create } from 'zustand';
import { queries } from '~/api';
import { $api } from '~/api/openapi';
import type { BasicPortfolioView } from '~/api/queries/types';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { CreatePortfolioButton } from './-components/create-portfolio-button';

export const Route = createFileRoute('/_authenticated/_member')({
  component: RouteComponent,
  beforeLoad: async ({ context: { queryClient } }) => {
    const onboardingCompleted = await queryClient.fetchQuery($api.queryOptions('get', '/api/onboarding/status'));

    if (!onboardingCompleted) {
      throw redirect({ to: '/onboarding' });
    }
  }
});

const NAV_LINKS = [
  {
    label: 'Dashboard',
    icon: <IconHome size={20} />,
    options: linkOptions({
      to: '/dashboard'
    })
  },
  {
    label: 'Positions',
    icon: <IconListDetails size={20} />,
    options: linkOptions({
      to: '/positions'
    })
  },
  {
    label: 'Trades',
    icon: <IconArrowsLeftRight size={20} />,
    options: linkOptions({
      to: '/trades'
    })
  }
];

function createPortfolioLink(portfolio: BasicPortfolioView) {
  return {
    key: `portfolio-sidebar-link-${portfolio.id}`,
    label: portfolio.name,
    icon: <IconFolderOpen size={20} />,
    options: linkOptions({
      to: '/portfolios/$portfolioId',
      params: { portfolioId: portfolio.id }
    })
  };
}

export const useSidebarState = create<{ opened: boolean; open: () => void; close: () => void }>((set) => ({
  opened: false,

  open: () => set({ opened: true }),
  close: () => set({ opened: false })
}));

function RouteComponent() {
  const { opened, close } = useSidebarState();
  const isMobile = useMediaQuery('(max-width: 1023px)');

  useEffect(() => {
    if (!isMobile) {
      close();
    }
  }, [isMobile, close]);

  return (
    <div className="layout-wrapper">
      {isMobile ? (
        <Drawer.Root
          opened={opened}
          onClose={close}
          size="18rem"
          styles={{
            content: {
              border: 0
            }
          }}>
          <Drawer.Overlay />
          <Drawer.Content>
            <Sidebar />
          </Drawer.Content>
        </Drawer.Root>
      ) : (
        <Sidebar />
      )}
      {/* Main Container */}
      <main className="main-content custom-scrollbar">
        <Outlet />
      </main>

      <style>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(100, 100, 100, 0.2); border-radius: 10px; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(100, 100, 100, 0.4); }
        .no-scrollbar::-webkit-scrollbar { display: none; }
        .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
      `}</style>
    </div>
  );
}

const Sidebar = () => {
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const navigate = useNavigate();
  const { logout } = useAuthentication();

  const { data: portfolioLinks } = useSuspenseQuery({
    ...queries.portfolio.fetchPortfolios(),
    select: (data) => data.map((portfolio) => createPortfolioLink(portfolio))
  });

  const toggleTheme = () => {
    // Check if browser supports View Transitions
    if (!document.startViewTransition) {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
      return;
    }

    // Animates the entire DOM change
    document.startViewTransition(() => {
      setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
    });
  };

  return (
    <aside className="sidebar">
      <div className="logo-container">
        <div className="logo-group">
          <div className="logo-icon-box">
            <IconTrendingUp className="logo-icon" />
          </div>
          <span className="logo-text">CANVERSE</span>
        </div>
      </div>
      <div className="sidebar-nav-container custom-scrollbar no-scrollbar">
        <div className="nav-section">
          <p className="nav-label" style={{ marginTop: 0 }}>
            Menu
          </p>
          <nav className="nav-list">
            {NAV_LINKS.map((link) => (
              <SidebarLink key={link.options.to} label={link.label} icon={link.icon} options={link.options} onClick={() => {}} />
            ))}
          </nav>
        </div>
        <div className="nav-section" style={{ marginTop: '0.5rem' }}>
          <div
            style={{
              padding: '0 1rem',
              marginBottom: '0.5rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
            <p className="nav-label" style={{ padding: 0, margin: 0 }}>
              Portfolios
            </p>
            <CreatePortfolioButton />
          </div>
          <nav className="nav-list">
            {portfolioLinks.map((link) => (
              <SidebarLink key={link.key} label={link.label} icon={link.icon} options={link.options} onClick={() => {}} />
            ))}
          </nav>
        </div>
      </div>
      <div className="sidebar-footer">
        <div className="pro-card">
          <div className="pro-card-circle" />
          <p className="pro-tag">
            <IconZoomPan size={14} fill="currentColor" /> AI VERSION
          </p>
          <p className="pro-desc">Get real-time AI insights.</p>
          <button type="button" className="btn-upgrade">
            UPGRADE
          </button>
        </div>
        <button type="button" onClick={toggleTheme} className="sidebar-action-btn">
          <div className="action-icon-box theme-icon-box">{colorScheme === 'light' ? <IconSun size={18} /> : <IconMoon size={18} />}</div>
          <span className="action-label">{colorScheme === 'light' ? 'Dark UI' : 'Light UI'}</span>
        </button>

        {/* Sign Out */}
        <button
          type="button"
          className="sidebar-action-btn logout-btn"
          onClick={() => {
            logout();
            navigate({ to: '/login' });
          }}>
          <div className="action-icon-box">
            <IconLogout size={18} />
          </div>
          <span className="action-label">Sign Out</span>
        </button>
      </div>
    </aside>
  );
};

const SidebarLink = ({ icon, label, options, onClick }) => {
  return (
    <Link
      {...options}
      onClick={onClick}
      // This is the TanStack way to apply classes based on active state
      className="sidebar-link-wrapper">
      {({ isActive }) => (
        <div className={`sidebar-link ${isActive ? 'is-active' : ''}`}>
          {/* Active indicator bar */}
          {isActive && <div className="active-indicator" />}

          <div className="link-icon-box">{icon}</div>

          <span className="link-label">{label}</span>

          {isActive && <IconChevronRight size={14} className="link-chevron" />}
        </div>
      )}
    </Link>
  );
};
