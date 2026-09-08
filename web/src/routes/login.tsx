import { createFileRoute, redirect } from '@tanstack/react-router';
import { getAccessToken } from '@/api/client';
import { LoginPage } from '@/features/auth/pages/login';

export const Route = createFileRoute('/login')({
  beforeLoad: ({ context }) => {
    const isAuthed = context.auth.isAuthenticated || Boolean(getAccessToken());
    if (isAuthed) {
      throw redirect({ to: '/' });
    }
  },
  component: LoginPage
});
