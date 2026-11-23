import { createFileRoute, redirect } from '@tanstack/react-router';
import { LoginPage } from '~/features/auth/pages/login-page';

export const Route = createFileRoute('/(auth)/login')({
  head: () => ({
    meta: [
      {
        name: 'description',
        content: 'Log in to your Canverse account to access your portfolio and manage your investments.'
      },
      {
        name: 'keywords',
        content: 'Canverse, login, portfolio, investments, account'
      },
      {
        name: 'robots',
        content: 'index, follow'
      }
    ]
  }),
  component: LoginPage,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/dashboard' });
  }
});
