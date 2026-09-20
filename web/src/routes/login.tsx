import { Center, Loader } from '@mantine/core';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/auth-state';
import { resolveSession } from '@/api/session';
import { LoginPage } from '@/features/auth/pages/login';
import { siteConfig } from '@/shared/config/site';
import { isStandaloneApp } from '@/shared/utils/is-standalone-app';
import { createSeoMeta } from '@/shared/utils/seo';

interface LoginSearch {
  redirect?: string;
}

function isInternalAppPath(value: string) {
  return value.startsWith('/') && !value.startsWith('//');
}

export const Route = createFileRoute('/login')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Sign in',
      description: `Sign in to access your ${siteConfig.name} account.`,
      path: '/login'
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  validateSearch: (search: Record<string, unknown>): LoginSearch => {
    const redirectParam = search.redirect;
    return {
      redirect: typeof redirectParam === 'string' && isInternalAppPath(redirectParam) ? redirectParam : undefined
    };
  },
  beforeLoad: async ({ context }) => {
    // Standalone launches must also check the refresh cookie because the
    // access token may have been lost when the installed app was closed.
    if (getAccessToken() === null && !isStandaloneApp()) {
      return;
    }

    const session = await resolveSession(context.queryClient);
    if (session.status === 'authenticated') {
      throw redirect({ to: '/app', replace: true });
    }
  },
  pendingComponent: () => (
    <Center h="100vh">
      <Loader size="lg" />
    </Center>
  ),
  component: LoginPage
});
