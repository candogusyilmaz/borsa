import { createFileRoute, redirect } from '@tanstack/react-router';
import { RegisterPage } from '~/features/auth/pages/register-page';

export const Route = createFileRoute('/(auth)/register')({
  head: () => ({
    meta: [
      {
        name: 'description',
        content: 'Create your Canverse account to track your portfolios and trades.'
      },
      {
        name: 'keywords',
        content: 'register, sign up, Canverse, portfolio, trades, account'
      },
      {
        name: 'robots',
        content: 'index, follow'
      },
      {
        property: 'og:title',
        content: 'Create account | Canverse'
      },
      {
        property: 'og:description',
        content: 'Create your Canverse account to track your portfolios and trades.'
      },
      {
        property: 'og:type',
        content: 'website'
      },
      {
        property: 'og:url',
        content: 'https://borsa.canverse.dev/register'
      },
      {
        property: 'og:image',
        content: 'https://borsa.canverse.dev/assets/logo.png'
      }
    ]
  }),
  component: RegisterPage,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/dashboard' });
  }
});
