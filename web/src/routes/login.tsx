import { createFileRoute, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/client';
import { LoginPage } from '@/features/auth/pages/login';

interface LoginSearch {
  redirect?: string;
}

function isInternalAppPath(value: string): boolean {
  return value.startsWith('/') && !value.startsWith('//');
}

export const Route = createFileRoute('/login')({
  validateSearch: (search: Record<string, unknown>): LoginSearch => {
    const redirectParam = search.redirect;
    return {
      redirect: typeof redirectParam === 'string' && isInternalAppPath(redirectParam) ? redirectParam : undefined
    };
  },
  beforeLoad: () => {
    if (getAccessToken()) {
      throw redirect({ to: '/', replace: true });
    }
  },
  component: LoginPage
});
