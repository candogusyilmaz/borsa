import { MantineProvider } from '@mantine/core';
import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { User } from '@/shared/types/auth';
import { DashboardPage } from './index';

// Mock TanStack Router
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, className, ...props }: { children: ReactNode; to: string; className?: string; [key: string]: unknown }) => (
    <a href={to} className={className} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => vi.fn()
}));

describe('DashboardPage', () => {
  const mockUser: User = {
    id: 'usr_abc1234567',
    email: 'investor@example.com',
    createdAt: '2026-02-15T12:00:00Z'
  };

  it('renders primary semantic h1 heading and welcome greeting with parsed user name', () => {
    const html = renderToString(
      <MantineProvider>
        <DashboardPage user={mockUser} />
      </MantineProvider>
    );

    expect(html).toContain('Welcome back, investor');
    expect(html).toContain('<h1');
  });

  it('renders default user fallback when email format is unusual', () => {
    const fallbackUser: User = {
      id: 'usr_none',
      email: '',
      createdAt: '2026-01-01T00:00:00Z'
    };

    const html = renderToString(
      <MantineProvider>
        <DashboardPage user={fallbackUser} />
      </MantineProvider>
    );

    expect(html).toContain('Welcome back, User');
  });

  it('renders mobile-first hero portfolio metrics and quick action buttons', () => {
    const html = renderToString(
      <MantineProvider>
        <DashboardPage user={mockUser} />
      </MantineProvider>
    );

    expect(html).toContain('Total Portfolio Value');
    expect(html).toContain('$142,850.40');
    expect(html).toContain('+$3,210.80 (+2.30%) Today');
    expect(html).toContain('Trade');
    expect(html).toContain('Deposit');
    expect(html).toContain('Transfer');
  });

  it('renders market watchlist tickers and recent activities', () => {
    const html = renderToString(
      <MantineProvider>
        <DashboardPage user={mockUser} />
      </MantineProvider>
    );

    // Watchlist items
    expect(html).toContain('AAPL');
    expect(html).toContain('NVDA');
    expect(html).toContain('BIST 100');
    expect(html).toContain('THYAO');

    // Recent activities
    expect(html).toContain('Recent Activity');
    expect(html).toContain('AAPL Cash Dividend Settled');
  });

  it('renders foundation operational information for authenticated session', () => {
    const html = renderToString(
      <MantineProvider>
        <DashboardPage user={mockUser} />
      </MantineProvider>
    );

    expect(html).toContain('Foundation Operational');
    expect(html).toContain('investor@example.com');
  });
});
