import { Center, Loader } from '@mantine/core';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/auth-state';
import { resolveSession } from '@/api/session';
import { RegisterPage } from '@/features/auth/pages/register';
import { siteConfig } from '@/shared/config/site';
import { isStandaloneApp } from '@/shared/utils/is-standalone-app';
import { createSeoMeta } from '@/shared/utils/seo';

export interface RegisterSearch {
  redirect?: string;
}

export function isInternalAppPath(value: string) {
  return value.startsWith('/') && !value.startsWith('//');
}

export const Route = createFileRoute('/register')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Sign up',
      description: `Create your ${siteConfig.name} account to access real-time simulation and market intelligence.`,
      path: '/register'
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  validateSearch: (search: Record<string, unknown>): RegisterSearch => {
    const redirectParam = search.redirect;
    return {
      redirect: typeof redirectParam === 'string' && isInternalAppPath(redirectParam) ? redirectParam : undefined
    };
  },
  beforeLoad: async ({ context, search }) => {
    if (getAccessToken() === null && !isStandaloneApp()) {
      return;
    }

    const session = await resolveSession(context.queryClient);
    if (session.status === 'authenticated') {
      throw redirect({ to: search.redirect ?? '/app', replace: true });
    }
  },
  pendingComponent: () => (
    <Center h="100vh">
      <Loader size="lg" />
    </Center>
  ),
  component: RegisterPage
});
