import { useLingui } from '@lingui/react/macro';
import { useLocation } from '@tanstack/react-router';
import { useEffect } from 'react';

export function DocumentTitle() {
  const { t } = useLingui();
  const pathname = useLocation({ select: (location) => location.pathname });
  const labels: Record<string, string | undefined> = {
    '/login': t`Sign in`,
    '/register': t`Create account`,
    '/onboarding': t`Set up your account`,
    '/dashboard': t`Dashboard`,
    '/positions': t`Positions`,
    '/trades': t`Trades`
  };
  const label = pathname.startsWith('/portfolios/') ? t`Portfolio` : labels[pathname];

  useEffect(() => {
    document.title = label ? `${label} · Canverse` : 'Borsa | Canverse';
  }, [label]);

  return null;
}
