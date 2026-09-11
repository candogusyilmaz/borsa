import { MantineProvider } from '@mantine/core';
import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '@/shared/types/auth';
import { AppShell } from './index';

let mockLocationState = {
  pathname: '/app',
  search: {} as Record<string, unknown>
};

// Mock TanStack Router
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, className, ...props }: { children: ReactNode; to: string; className?: string; [key: string]: unknown }) => (
    <a href={to} className={className} {...props}>
      {children}
    </a>
  ),
  useNavigate: () => vi.fn(),
  useRouterState: () => ({
    location: mockLocationState
  })
}));

// Mock Auth
vi.mock('@/shared/hooks/use-auth', () => ({
  useAuth: () => ({
    logout: vi.fn(),
    login: vi.fn()
  })
}));

describe('AppShell mobile-first dashboard layout', () => {
  const mockUser: User = {
    id: 'usr_1234567890',
    email: 'trader@canverse.dev',
    createdAt: '2026-01-01T00:00:00Z'
  };

  beforeEach(() => {
    mockLocationState = {
      pathname: '/app',
      search: {}
    };
  });

  it('renders mobile-first semantic structure without desktop sidebar or bottom dock', () => {
    const html = renderToString(
      <MantineProvider>
        <AppShell user={mockUser}>
          <div data-testid="dashboard-content">Dashboard Main Content</div>
        </AppShell>
      </MantineProvider>
    );

    // Skip link
    expect(html).toContain('Skip to main content');
    expect(html).toContain('href="#main-content"');

    // Content
    expect(html).toContain('Dashboard Main Content');

    // Confirm old Mantine AppShell classes are NOT used
    expect(html).not.toContain('mantine-AppShell-navbar');

    // Confirm bottom dock is removed
    expect(html).not.toContain('aria-label="Mobile Navigation Dock"');
  });

  it('renders header with brand, live status, theme toggle, and account trigger', () => {
    const html = renderToString(
      <MantineProvider>
        <AppShell user={mockUser}>
          <div>Child</div>
        </AppShell>
      </MantineProvider>
    );

    expect(html).toContain('Markets Live');
    expect(html).toContain('aria-label="Account and Settings Menu"');
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain('aria-haspopup="dialog"');
  });

  it('matches login screen background styling and variables', async () => {
    const fs = await import('node:fs');
    const path = await import('node:path');

    const appShellCss = fs.readFileSync(path.resolve(import.meta.dirname, './app-shell.module.css'), 'utf-8');
    const loginCss = fs.readFileSync(path.resolve(import.meta.dirname, '../../../features/auth/pages/login/login.module.css'), 'utf-8');

    const expectedBackground = `background-color: var(--mantine-color-body);
  background-image:
    radial-gradient(circle at 15% 15%, rgb(98 91 246 / 0.08) 0%, transparent 40%),
    radial-gradient(circle at 85% 85%, rgb(98 91 246 / 0.05) 0%, transparent 40%);`;

    expect(appShellCss).toContain(expectedBackground);
    expect(loginCss).toContain(expectedBackground);
  });
});
