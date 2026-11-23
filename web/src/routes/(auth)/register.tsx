import { createFileRoute } from '@tanstack/react-router';
import { RegisterPage } from '~/features/auth/pages/register-page';

export const Route = createFileRoute('/(auth)/register')({
  head: () => ({
    meta: [
      {
        title: 'Register | Canverse'
      },
      {
        name: 'description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
      },
      {
        name: 'keywords',
        content: 'register, sign up, Canverse, collaboration, account creation'
      },
      {
        name: 'robots',
        content: 'index, follow'
      },
      {
        property: 'og:title',
        content: 'Register | Canverse'
      },
      {
        property: 'og:description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
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
        content: 'https://borsa.canverse.dev/assets/favicon.png'
      }
    ]
  }),
  component: RegisterPage
});
