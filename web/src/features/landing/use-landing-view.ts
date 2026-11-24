import type { MessageDescriptor } from '@lingui/core';
import { msg } from '@lingui/core/macro';
import { IconArrowsLeftRight, IconBriefcase, IconChartPie, IconLayoutDashboard } from '@tabler/icons-react';
import type { ComponentType } from 'react';
import { useAuthentication } from '~/features/auth/authentication-context';

export interface LandingCapability {
  id: string;
  icon: ComponentType<{ size?: number; stroke?: number; 'aria-hidden'?: boolean }>;
  title: MessageDescriptor;
  description: MessageDescriptor;
}

export interface LandingDetail {
  id: string;
  title: MessageDescriptor;
  description: MessageDescriptor;
}

const capabilities: readonly LandingCapability[] = [
  {
    id: 'dashboards',
    icon: IconLayoutDashboard,
    title: msg`Dashboards`,
    description: msg`Group portfolios into custom dashboards to see total balance, daily change, realized P&L, and win rate.`
  },
  {
    id: 'portfolios',
    icon: IconBriefcase,
    title: msg`Portfolios`,
    description: msg`Organize holdings across separate portfolios with per-currency totals, asset allocation, and monthly earnings.`
  },
  {
    id: 'positions',
    icon: IconChartPie,
    title: msg`Positions`,
    description: msg`Track open positions with average cost, current market value, portfolio weight, and unrealized return.`
  },
  {
    id: 'trades',
    icon: IconArrowsLeftRight,
    title: msg`Trades`,
    description: msg`Log single or bulk buy and sell trades, filter your history by portfolio or type, and undo eligible entries.`
  }
];

const details: readonly LandingDetail[] = [
  {
    id: 'multi-currency',
    title: msg`Multi-currency`,
    description: msg`Keep balances and returns separated by currency without mixing totals.`
  },
  {
    id: 'sign-in',
    title: msg`Sign-in options`,
    description: msg`Sign in with email and password or continue with your Google account.`
  },
  {
    id: 'appearance',
    title: msg`Themes and languages`,
    description: msg`Switch between light and dark mode, and use the interface in English or Turkish.`
  }
];

export const CONTACT_EMAIL_HREF = 'mailto:candogusyilmaz@gmail.com';

export function useLandingView() {
  const { isAuthenticated } = useAuthentication();

  return {
    isAuthenticated,
    capabilities,
    details,
    contactHref: CONTACT_EMAIL_HREF
  };
}

export type LandingViewModel = ReturnType<typeof useLandingView>;
