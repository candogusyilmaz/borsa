import { Center, Loader } from '@mantine/core';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/client';
import { resolveSession } from '@/api/session';
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
  beforeLoad: async ({ context }) => {
    // Session restoration is owned by the protected route. The login route
    // only verifies an already-present access-token session, so a genuinely
    // anonymous visitor renders login without any refresh-cookie attempt.
    if (getAccessToken() === null) {
      return;
    }

    const session = await resolveSession(context.queryClient);
    if (session.status === 'authenticated') {
      throw redirect({ to: '/', replace: true });
    }
  },
  pendingComponent: () => (
    <Center h="100vh">
      <Loader size="lg" />
    </Center>
  ),
  component: LoginPage
});
